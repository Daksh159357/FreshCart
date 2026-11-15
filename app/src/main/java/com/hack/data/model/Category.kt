package com.hack.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Category(
    var id: String = "",
    var name: String = "",
    var order: Int = 0
)
