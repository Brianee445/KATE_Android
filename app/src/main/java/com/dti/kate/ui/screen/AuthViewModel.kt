// app/src/main/java/com/dti/kate/ui/screen/AuthViewModel.kt
package com.dti.kate.ui.screen

class AuthViewModel {
    fun login(email: String, password: String, callback: (Boolean, String?) -> Unit) {
        // Replace with real authentication logic
        callback(true, null)
    }
    fun loginWithGoogle() { /* TODO */ }
    fun register(email: String, password: String, fullName: String, callback: (Boolean, String?) -> Unit) {
        callback(true, null)
    }
    fun registerWithGoogle() { /* TODO */ }
    fun resetPassword(email: String, callback: (Boolean, String?) -> Unit) {
        callback(true, null)
    }
}
