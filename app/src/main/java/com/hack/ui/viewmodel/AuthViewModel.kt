package com.hack.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.FreshCartApplication
import com.hack.data.repository.GroceryRepository
import com.hack.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthScreenState {
    object Idle : AuthScreenState()
    object Loading : AuthScreenState()
    data class Error(val message: String) : AuthScreenState()
    data class Authenticated(val role: String) : AuthScreenState()
}

class AuthViewModel(
    private val repository: GroceryRepository = FreshCartApplication.instance.repository,
    private val sessionManager: SessionManager = FreshCartApplication.instance.sessionManager
) : ViewModel() {

    private val _state = MutableStateFlow<AuthScreenState>(
        when (sessionManager.currentRole) {
            SessionManager.ROLE_CUSTOMER -> AuthScreenState.Authenticated(SessionManager.ROLE_CUSTOMER)
            SessionManager.ROLE_ADMIN -> AuthScreenState.Authenticated(SessionManager.ROLE_ADMIN)
            else -> AuthScreenState.Idle
        }
    )
    val state: StateFlow<AuthScreenState> = _state.asStateFlow()

    fun continueAsCustomer() {
        sessionManager.currentRole = SessionManager.ROLE_CUSTOMER
        _state.value = AuthScreenState.Authenticated(SessionManager.ROLE_CUSTOMER)
    }

    fun loginAsAdmin(password: String) {
        if (password.isBlank()) {
            _state.value = AuthScreenState.Error("Please enter the password.")
            return
        }

        _state.value = AuthScreenState.Loading
        viewModelScope.launch {
            val isValid = repository.verifyAdminPassword(password.trim())
            if (isValid) {
                sessionManager.currentRole = SessionManager.ROLE_ADMIN
                sessionManager.isAdminActive = true
                _state.value = AuthScreenState.Authenticated(SessionManager.ROLE_ADMIN)
            } else {
                _state.value = AuthScreenState.Error("Incorrect password. Please try again.")
            }
        }
    }

    fun resetState() {
        _state.value = AuthScreenState.Idle
    }

    fun logout() {
        sessionManager.clearSession()
        _state.value = AuthScreenState.Idle
    }
}
