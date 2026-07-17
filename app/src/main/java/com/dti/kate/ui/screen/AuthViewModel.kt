// app/src/main/java/com/dti/kate/ui/screen/AuthViewModel.kt
package com.dti.kate.ui.screen

import android.content.Context
import com.dti.kate.repository.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AuthViewModel(context: Context) {

    private val repository = Repository(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun login(email: String, password: String, callback: (Boolean, String?) -> Unit) {
        scope.launch {
            repository.login(email, password).fold(
                onSuccess = { callback(true, null) },
                onFailure = { error -> callback(false, error.message ?: "Login failed") }
            )
        }
    }

    fun loginWithGoogle() {
        // TODO: no Google OAuth endpoint exists on KateApiService yet -
        // add one (e.g. POST api/v1/auth/google) before wiring this up.
    }

    fun register(email: String, password: String, fullName: String, callback: (Boolean, String?) -> Unit) {
        scope.launch {
            repository.register(email, password, fullName.ifBlank { null }).fold(
                onSuccess = { callback(true, null) },
                onFailure = { error -> callback(false, error.message ?: "Registration failed") }
            )
        }
    }

    fun registerWithGoogle() {
        // TODO: same as loginWithGoogle - needs a backend endpoint first.
    }

    fun resetPassword(email: String, callback: (Boolean, String?) -> Unit) {
        scope.launch {
            repository.forgotPassword(email).fold(
                onSuccess = { callback(true, null) },
                onFailure = { error -> callback(false, error.message ?: "Failed to send reset email") }
            )
        }
    }
}
