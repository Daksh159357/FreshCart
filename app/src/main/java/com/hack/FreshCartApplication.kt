package com.hack

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.hack.data.repository.GroceryRepository
import com.hack.data.session.SessionManager

class FreshCartApplication : Application() {

    lateinit var sessionManager: SessionManager
        private set

    lateinit var repository: GroceryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        FirebaseApp.initializeApp(this)

        try {
            val db = FirebaseDatabase.getInstance("https://hack-94d42-default-rtdb.firebaseio.com")
            db.setPersistenceEnabled(true)
        } catch (_: Exception) {
            // Already initialized persistence
        }

        sessionManager = SessionManager(this)
        repository = GroceryRepository()

        // Anonymous auth sign-in under the hood if not signed in
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
        }
    }

    companion object {
        lateinit var instance: FreshCartApplication
            private set
    }
}
