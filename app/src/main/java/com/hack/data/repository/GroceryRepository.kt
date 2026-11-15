package com.hack.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.hack.data.model.CartItem
import com.hack.data.model.Category
import com.hack.data.model.Order
import com.hack.data.model.OrderItem
import com.hack.data.model.Product
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

class GroceryRepository(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance("https://hack-94d42-default-rtdb.firebaseio.com")
) {
    private val productsRef = database.getReference("products")
    private val categoriesRef = database.getReference("categories")
    private val ordersRef = database.getReference("orders")
    private val configRef = database.getReference("config")
    private val connectedRef = database.getReference(".info/connected")

    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    init {
        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _isConnected.value = snapshot.getValue(Boolean::class.java) ?: true
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // ---------------- Real-time Products Flow ----------------
    fun getProductsFlow(): Flow<List<Product>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Product>()
                for (child in snapshot.children) {
                    val p = child.getValue(Product::class.java)
                    if (p != null) {
                        if (p.id.isEmpty()) p.id = child.key ?: ""
                        list.add(p)
                    }
                }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        productsRef.addValueEventListener(listener)
        awaitClose { productsRef.removeEventListener(listener) }
    }

    // ---------------- Real-time Categories Flow ----------------
    fun getCategoriesFlow(): Flow<List<Category>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Category>()
                for (child in snapshot.children) {
                    val c = child.getValue(Category::class.java)
                    if (c != null) {
                        if (c.id.isEmpty()) c.id = child.key ?: ""
                        list.add(c)
                    }
                }
                list.sortBy { it.order }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        categoriesRef.addValueEventListener(listener)
        awaitClose { categoriesRef.removeEventListener(listener) }
    }

    // ---------------- Real-time Orders Flow (All) ----------------
    fun getAllOrdersFlow(): Flow<List<Order>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Order>()
                for (child in snapshot.children) {
                    val order = child.getValue(Order::class.java)
                    if (order != null) {
                        if (order.id.isEmpty()) order.id = child.key ?: ""
                        list.add(order)
                    }
                }
                list.sortByDescending { it.createdAt }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ordersRef.addValueEventListener(listener)
        awaitClose { ordersRef.removeEventListener(listener) }
    }

    // ---------------- Real-time Customer Orders Flow ----------------
    fun getCustomerOrdersFlow(customerId: String): Flow<List<Order>> = callbackFlow {
        val query = ordersRef.orderByChild("customerId").equalTo(customerId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Order>()
                for (child in snapshot.children) {
                    val order = child.getValue(Order::class.java)
                    if (order != null) {
                        if (order.id.isEmpty()) order.id = child.key ?: ""
                        list.add(order)
                    }
                }
                list.sortByDescending { it.createdAt }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    // ---------------- Customer: Place Order ----------------
    suspend fun placeOrder(customerId: String, cartItems: List<CartItem>): Result<String> {
        return try {
            val orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).uppercase()
            val now = System.currentTimeMillis()

            val orderItems = cartItems.map { item ->
                OrderItem(
                    productId = item.product.id,
                    name = item.product.name,
                    price = item.product.price,
                    qty = item.quantity
                )
            }
            val total = cartItems.sumOf { it.lineTotal }

            // Atomically decrement stock for each item
            for (item in cartItems) {
                val prodRef = productsRef.child(item.product.id).child("stock")
                prodRef.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(mutableData: MutableData): Transaction.Result {
                        val currentStock = mutableData.getValue(Int::class.java) ?: 0
                        val newStock = (currentStock - item.quantity).coerceAtLeast(0)
                        mutableData.value = newStock
                        return Transaction.success(mutableData)
                    }

                    override fun onComplete(
                        error: DatabaseError?,
                        committed: Boolean,
                        currentData: DataSnapshot?
                    ) {}
                })
            }

            val order = Order(
                id = orderId,
                customerId = customerId,
                items = orderItems,
                total = total,
                status = "Pending",
                createdAt = now
            )

            ordersRef.child(orderId).setValue(order).await()
            Result.success(orderId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------- Admin: Add / Edit Product ----------------
    suspend fun saveProduct(product: Product): Result<Unit> {
        return try {
            val id = if (product.id.isEmpty()) {
                productsRef.push().key ?: ("prod_" + System.currentTimeMillis())
            } else {
                product.id
            }
            val now = System.currentTimeMillis()
            val toSave = product.copy(
                id = id,
                createdAt = if (product.createdAt == 0L) now else product.createdAt,
                updatedAt = now
            )
            productsRef.child(id).setValue(toSave).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------- Admin: Quick Update Stock & Price ----------------
    suspend fun updateStock(productId: String, newStock: Int): Result<Unit> {
        return try {
            val updates = mapOf<String, Any>(
                "stock" to newStock.coerceAtLeast(0),
                "updatedAt" to System.currentTimeMillis()
            )
            productsRef.child(productId).updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePrice(productId: String, newPrice: Double): Result<Unit> {
        return try {
            val updates = mapOf<String, Any>(
                "price" to newPrice,
                "updatedAt" to System.currentTimeMillis()
            )
            productsRef.child(productId).updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------- Admin: Delete Product ----------------
    suspend fun deleteProduct(productId: String): Result<Unit> {
        return try {
            productsRef.child(productId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------- Admin: Categories CRUD ----------------
    suspend fun addCategory(name: String, order: Int): Result<Unit> {
        return try {
            val id = "cat_" + name.lowercase().replace("\\s+".toRegex(), "_")
            val category = Category(id = id, name = name, order = order)
            categoriesRef.child(id).setValue(category).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameCategory(categoryId: String, newName: String): Result<Unit> {
        return try {
            categoriesRef.child(categoryId).child("name").setValue(newName).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCategory(categoryId: String, categoryName: String): Result<Unit> {
        return try {
            // Delete category record
            categoriesRef.child(categoryId).removeValue().await()

            // Update any products with this category to 'Uncategorized'
            val snapshot = productsRef.orderByChild("category").equalTo(categoryName).get().await()
            for (child in snapshot.children) {
                child.ref.child("category").setValue("Uncategorized").await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------- Admin: Update Order Status ----------------
    suspend fun updateOrderStatus(order: Order, newStatus: String): Result<Unit> {
        return try {
            val oldStatus = order.status
            ordersRef.child(order.id).child("status").setValue(newStatus).await()

            // If cancelling an order, RESTORE the stock of the items
            if (newStatus == "Cancelled" && oldStatus != "Cancelled") {
                for (item in order.items) {
                    val prodRef = productsRef.child(item.productId).child("stock")
                    prodRef.runTransaction(object : Transaction.Handler {
                        override fun doTransaction(mutableData: MutableData): Transaction.Result {
                            val current = mutableData.getValue(Int::class.java) ?: 0
                            mutableData.value = current + item.qty
                            return Transaction.success(mutableData)
                        }

                        override fun onComplete(
                            error: DatabaseError?,
                            committed: Boolean,
                            currentData: DataSnapshot?
                        ) {}
                    })
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------- Admin: Password Verification ----------------
    suspend fun verifyAdminPassword(password: String): Boolean {
        return try {
            val snapshot = configRef.child("adminPassword").get().await()
            val remotePass = snapshot.getValue(String::class.java) ?: "admin123"

            // Direct comparison or SHA-256 hash comparison
            val hashedInput = sha256(password)
            password == remotePass || hashedInput.equals(remotePass, ignoreCase = true)
        } catch (e: Exception) {
            // Fallback default password in case of network edge case
            password == "admin123"
        }
    }

    private fun sha256(base: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(base.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (ex: Exception) {
            base
        }
    }
}
