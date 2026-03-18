/* Begin, prompt: Generate the Firebase Authentication logic... focus only on the logic and state management (e.g., using Kotlin Flows or LiveData). */
package com.example.whichzup.chat.domain.model
sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Authenticated(val user: User) : AuthState()
    object Unauthenticated : AuthState()
    data class Error(val message: String) : AuthState()
}
/* End */