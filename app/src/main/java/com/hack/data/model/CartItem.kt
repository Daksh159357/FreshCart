package com.hack.data.model

data class CartItem(
    val product: Product,
    var quantity: Int = 1
) {
    val lineTotal: Double get() = product.price * quantity
}
