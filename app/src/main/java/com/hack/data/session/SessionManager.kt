package com.hack.data.session

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("freshcart_session_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ROLE = "user_role"
        private const val KEY_CUSTOMER_ID = "customer_id"
        private const val KEY_ADMIN_ACTIVE = "admin_active"
        const val ROLE_CUSTOMER = "customer"
        const val ROLE_ADMIN = "admin"
    }

    var currentRole: String?
        get() = prefs.getString(KEY_ROLE, null)
        set(value) = prefs.edit().putString(KEY_ROLE, value).apply()

    var customerId: String
        get() {
            var id = prefs.getString(KEY_CUSTOMER_ID, null)
            if (id.isNullOrEmpty()) {
                id = "CUST-" + UUID.randomUUID().toString().substring(0, 8).uppercase()
                prefs.edit().putString(KEY_CUSTOMER_ID, id).apply()
            }
            return id
        }
        set(value) = prefs.edit().putString(KEY_CUSTOMER_ID, value).apply()

    var isAdminActive: Boolean
        get() = prefs.getBoolean(KEY_ADMIN_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_ADMIN_ACTIVE, value).apply()

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ROLE)
            .remove(KEY_ADMIN_ACTIVE)
            .apply()
    }
}
