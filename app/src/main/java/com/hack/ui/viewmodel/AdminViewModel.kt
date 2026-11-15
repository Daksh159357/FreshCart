package com.hack.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.FreshCartApplication
import com.hack.data.model.Category
import com.hack.data.model.Order
import com.hack.data.model.Product
import com.hack.data.repository.GroceryRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AdminDashboardStats(
    val totalProducts: Int = 0,
    val outOfStockCount: Int = 0,
    val lowStockCount: Int = 0,
    val totalOrders: Int = 0,
    val pendingOrdersCount: Int = 0
)

class AdminViewModel(
    private val repository: GroceryRepository = FreshCartApplication.instance.repository
) : ViewModel() {

    // Real-time Products & Categories
    val products: StateFlow<List<Product>> = repository.getProductsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val categories: StateFlow<List<Category>> = repository.getCategoriesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allOrders: StateFlow<List<Order>> = repository.getAllOrdersFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Dashboard Statistics Computed Reactively
    val stats: StateFlow<AdminDashboardStats> = combine(products, allOrders) { prods, orders ->
        AdminDashboardStats(
            totalProducts = prods.size,
            outOfStockCount = prods.count { it.isOutOfStock },
            lowStockCount = prods.count { it.isLowStock },
            totalOrders = orders.size,
            pendingOrdersCount = orders.count { it.status.equals("Pending", ignoreCase = true) }
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, AdminDashboardStats())

    // Order Status Filter
    private val _orderFilter = MutableStateFlow("All")
    val orderFilter: StateFlow<String> = _orderFilter.asStateFlow()

    val filteredOrders: StateFlow<List<Order>> = combine(allOrders, _orderFilter) { orders, filter ->
        if (filter == "All") orders else orders.filter { it.status.equals(filter, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // UI Feedback Messages
    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()

    fun setOrderFilter(filter: String) {
        _orderFilter.value = filter
    }

    // Product CRUD
    fun saveProduct(
        id: String,
        name: String,
        description: String,
        category: String,
        priceStr: String,
        stockStr: String,
        imageUrl: String,
        onSuccess: () -> Unit
    ) {
        if (name.isBlank()) {
            viewModelScope.launch { _message.emit("Product name is required.") }
            return
        }

        val price = priceStr.toDoubleOrNull()
        if (price == null || price <= 0.0) {
            viewModelScope.launch { _message.emit("Please enter a valid price greater than 0.") }
            return
        }

        val stock = stockStr.toIntOrNull()
        if (stock == null || stock < 0) {
            viewModelScope.launch { _message.emit("Stock must be 0 or greater.") }
            return
        }

        val cleanImageUrl = normalizeImageUrl(imageUrl.trim())
        if (!cleanImageUrl.startsWith("https://", ignoreCase = true)) {
            viewModelScope.launch { _message.emit("Image URL must start with https://") }
            return
        }

        viewModelScope.launch {
            val product = Product(
                id = id,
                name = name.trim(),
                description = description.trim(),
                category = if (category.isBlank()) "Uncategorized" else category.trim(),
                price = price,
                stock = stock,
                imageUrl = cleanImageUrl
            )
            val result = repository.saveProduct(product)
            result.onSuccess {
                _message.emit("Product saved successfully.")
                onSuccess()
            }.onFailure {
                _message.emit("Failed to save product.")
            }
        }
    }

    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            repository.deleteProduct(productId).onSuccess {
                _message.emit("Product deleted.")
            }.onFailure {
                _message.emit("Failed to delete product.")
            }
        }
    }

    fun quickUpdateStock(productId: String, newStock: Int) {
        viewModelScope.launch {
            repository.updateStock(productId, newStock)
        }
    }

    fun quickUpdatePrice(productId: String, newPrice: Double) {
        if (newPrice <= 0.0) return
        viewModelScope.launch {
            repository.updatePrice(productId, newPrice)
        }
    }

    fun updateOrderStatus(order: Order, newStatus: String) {
        viewModelScope.launch {
            repository.updateOrderStatus(order, newStatus).onSuccess {
                _message.emit("Order updated to $newStatus.")
            }.onFailure {
                _message.emit("Failed to update order status.")
            }
        }
    }

    fun addCategory(name: String, onSuccess: () -> Unit) {
        if (name.isBlank()) return
        val currentMaxOrder = categories.value.maxOfOrNull { it.order } ?: 0
        viewModelScope.launch {
            repository.addCategory(name.trim(), currentMaxOrder + 1).onSuccess {
                _message.emit("Category added.")
                onSuccess()
            }
        }
    }

    fun renameCategory(categoryId: String, newName: String, onSuccess: () -> Unit) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renameCategory(categoryId, newName.trim()).onSuccess {
                _message.emit("Category updated.")
                onSuccess()
            }
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category.id, category.name).onSuccess {
                _message.emit("Category removed. Products moved to Uncategorized.")
            }
        }
    }

    // Helper: converts github.com/user/repo/blob/branch/image.jpg to raw.githubusercontent.com
    fun normalizeImageUrl(url: String): String {
        var u = url.trim()
        if (u.contains("github.com") && u.contains("/blob/")) {
            u = u.replace("github.com", "raw.githubusercontent.com").replace("/blob/", "/")
        }
        return u
    }
}
