package com.example.whichzup.chat.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.whichzup.chat.domain.model.AuthState
import com.example.whichzup.chat.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class PasswordResetState {
    object Idle : PasswordResetState()
    object Loading : PasswordResetState()
    data class Success(val message: String) : PasswordResetState()
    data class Error(val message: String) : PasswordResetState()
}

class LoginViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    // Expose the AuthState directly from the repository
    val authState: StateFlow<AuthState> = userRepository.authState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AuthState.Idle
        )

    private val _email = MutableStateFlow("")
    val email = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _passwordResetState = MutableStateFlow<PasswordResetState>(PasswordResetState.Idle)
    val passwordResetState = _passwordResetState.asStateFlow()

    fun onEmailChange(newEmail: String) {
        _email.value = newEmail
        // Clear reset state if user starts typing a new email
        if (_passwordResetState.value !is PasswordResetState.Idle) {
            _passwordResetState.value = PasswordResetState.Idle
        }
    }

    fun onPasswordChange(newPassword: String) {
        _password.value = newPassword
    }

    fun login() {
        if (_email.value.isBlank() || _password.value.isBlank()) return
        viewModelScope.launch {
            userRepository.signInWithEmailPassword(_email.value.trim(), _password.value.trim())
        }
    }

    fun register() {
        if (_email.value.isBlank() || _password.value.isBlank()) return
        viewModelScope.launch {
            // Using the first part of the email as a default name for registration
            val defaultName = _email.value.substringBefore("@")
            userRepository.signUpWithEmailPassword(_email.value.trim(), _password.value.trim(), defaultName)
        }
    }

    fun resetPassword() {
        val currentEmail = _email.value.trim()
        if (currentEmail.isBlank()) {
            _passwordResetState.value = PasswordResetState.Error("Please enter your email to reset the password.")
            return
        }

        _passwordResetState.value = PasswordResetState.Loading
        viewModelScope.launch {
            val result = userRepository.sendPasswordResetEmail(currentEmail)
            result.fold(
                onSuccess = {
                    _passwordResetState.value = PasswordResetState.Success("Password reset email sent!")
                },
                onFailure = { error ->
                    _passwordResetState.value = PasswordResetState.Error(error.message ?: "Failed to send reset email.")
                }
            )
        }
    }
}