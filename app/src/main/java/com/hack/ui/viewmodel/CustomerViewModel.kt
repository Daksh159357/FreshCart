package com.hack.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.FreshCartApplication
import com.hack.data.model.CartItem
import com.hack.data.model.Category
import com.hack.data.model.Order
import com.hack.data.model.Product
import com.hack.data.repository.GroceryRepository
import com.hack.data.session.SessionManager
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

enum class ProductSortOption(val title: String) {
    FEATURED("Featured"),
    PRICE_LOW_TO_HIGH("Price: Low to High"),
    PRICE_HIGH_TO_LOW("Price: High to Low"),
    NAME_ASC("Name: A to Z")
}

class CustomerViewModel(
    private val repository: GroceryRepository = FreshCartApplication.instance.repository,
    private val sessionManager: SessionManager = FreshCartApplication.instance.sessionManager
) : ViewModel() {

    val customerId: String = sessionManager.customerId

    // Real-time Raw Products from Database
    private val _rawProducts = repository.getProductsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Real-time Categories from Database
    val categories: StateFlow<List<Category>> = repository.getCategoriesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Search, Category Filter, and Sorting
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _sortOption = MutableStateFlow(ProductSortOption.FEATURED)
    val sortOption: StateFlow<ProductSortOption> = _sortOption.asStateFlow()

    // Filtered & Sorted Products
    val products: StateFlow<List<Product>> = combine(
        _rawProducts,
        _searchQuery,
        _selectedCategory,
        _sortOption
    ) { raw, query, category, sort ->
        var filtered = raw.filter { prod ->
            val matchesQuery = query.isBlank() || prod.name.contains(query, ignoreCase = true) ||
                    prod.description.contains(query, ignoreCase = true)
            val matchesCategory = category == "All" || prod.category.equals(category, ignoreCase = true)
            matchesQuery && matchesCategory
        }

        filtered = when (sort) {
            ProductSortOption.FEATURED -> filtered
            ProductSortOption.PRICE_LOW_TO_HIGH -> filtered.sortedBy { it.price }
            ProductSortOption.PRICE_HIGH_TO_LOW -> filtered.sortedByDescending { it.price }
            ProductSortOption.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
        }
        filtered
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Cart Management
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    val cartItemCount: StateFlow<Int> = MutableStateFlow(0).apply {
        viewModelScope.launch {
            _cartItems.collect { items ->
                value = items.sumOf { it.quantity }
            }
        }
    }

    val cartSubtotal: StateFlow<Double> = MutableStateFlow(0.0).apply {
        viewModelScope.launch {
            _cartItems.collect { items ->
                value = items.sumOf { it.lineTotal }
            }
        }
    }

    // Customer Past Orders
    val orders: StateFlow<List<Order>> = repository.getCustomerOrdersFlow(customerId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // UI Events & Feedback
    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    private val _orderPlacedEvent = MutableSharedFlow<String>()
    val orderPlacedEvent: SharedFlow<String> = _orderPlacedEvent.asSharedFlow()

    private val _selectedProductForDetail = MutableStateFlow<Product?>(null)
    val selectedProductForDetail: StateFlow<Product?> = _selectedProductForDetail.asStateFlow()

    val isConnected: StateFlow<Boolean> = repository.isConnected

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onCategorySelected(category: String) {
        _selectedCategory.value = category
    }

    fun onSortOptionChanged(option: ProductSortOption) {
        _sortOption.value = option
    }

    fun openProductDetail(product: Product) {
        _selectedProductForDetail.value = product
    }

    fun closeProductDetail() {
        _selectedProductForDetail.value = null
    }

    fun addToCart(product: Product, quantityToAdd: Int = 1) {
        if (product.isOutOfStock) {
            viewModelScope.launch { _userMessage.emit("Item is out of stock.") }
            return
        }

        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == product.id }

        if (index >= 0) {
            val existing = currentList[index]
            val newQty = existing.quantity + quantityToAdd
            if (newQty > product.stock) {
                viewModelScope.launch {
                    _userMessage.emit("Only ${product.stock} left in stock.")
                }
                currentList[index] = existing.copy(quantity = product.stock)
            } else {
                currentList[index] = existing.copy(quantity = newQty)
                viewModelScope.launch {
                    _userMessage.emit("Added to cart.")
                }
            }
        } else {
            val actualQty = quantityToAdd.coerceAtMost(product.stock)
            currentList.add(CartItem(product = product, quantity = actualQty))
            viewModelScope.launch {
                _userMessage.emit("Added to cart.")
            }
        }
        _cartItems.value = currentList
    }

    fun incrementQuantity(cartItem: CartItem) {
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == cartItem.product.id }
        if (index >= 0) {
            val product = cartItem.product
            if (cartItem.quantity + 1 > product.stock) {
                viewModelScope.launch {
                    _userMessage.emit("Only ${product.stock} left in stock.")
                }
            } else {
                currentList[index] = cartItem.copy(quantity = cartItem.quantity + 1)
                _cartItems.value = currentList
            }
        }
    }

    fun decrementQuantity(cartItem: CartItem) {
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == cartItem.product.id }
        if (index >= 0) {
            if (cartItem.quantity - 1 <= 0) {
                currentList.removeAt(index)
            } else {
                currentList[index] = cartItem.copy(quantity = cartItem.quantity - 1)
            }
            _cartItems.value = currentList
        }
    }

    fun removeFromCart(cartItem: CartItem) {
        _cartItems.value = _cartItems.value.filterNot { it.product.id == cartItem.product.id }
    }

    fun clearCart() {
        _cartItems.value = emptyList()
    }

    fun placeOrder() {
        val items = _cartItems.value
        if (items.isEmpty()) {
            viewModelScope.launch { _userMessage.emit("Your cart is empty.") }
            return
        }

        viewModelScope.launch {
            val result = repository.placeOrder(customerId, items)
            result.onSuccess { orderId ->
                clearCart()
                _orderPlacedEvent.emit(orderId)
            }.onFailure {
                _userMessage.emit("Unable to place order. Please check connection and try again.")
            }
        }
    }
}
