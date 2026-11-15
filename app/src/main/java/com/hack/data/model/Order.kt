package com.hack.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class OrderItem(
    var productId: String = "",
    var name: String = "",
    var price: Double = 0.0,
    var qty: Int = 1
)

@IgnoreExtraProperties
data class Order(
    var id: String = "",
    var customerId: String = "",
    var items: List<OrderItem> = emptyList(),
    var total: Double = 0.0,
    var status: String = "Pending", // Pending, Packed, Out for Delivery, Delivered, Cancelled
    var createdAt: Long = 0L
)
