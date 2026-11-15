package com.hack.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Product(
    var id: String = "",
    var name: String = "",
    var description: String = "",
    var category: String = "",
    var price: Double = 0.0,
    var stock: Int = 0,
    var imageUrl: String = "",
    var createdAt: Long = 0L,
    var updatedAt: Long = 0L
) {
    val isInStock: Boolean get() = stock > 0
    val isLowStock: Boolean get() = stock in 1..5
    val isOutOfStock: Boolean get() = stock <= 0
}
