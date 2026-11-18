package com.gogether.app.core.account

import com.gogether.app.core.model.Category
import com.gogether.app.core.model.User

/** Team backend adapter. It owns tokens/session validation; never return a caller-selected identity.
 * Implementations return canonical UserStatus.ACCOUNT profiles and complete profile persistence
 * before returning. Passwords must not be logged or stored by the app. Cancellation propagates.
 * Firebase configuration is intentionally not fabricated for the local development build.
 */
interface AccountBackend {
    suspend fun restoreSession(): User?
    suspend fun signIn(email: String, password: String): User
    suspend fun signUp(email: String, password: String, name: String, interests: Set<Category>): User
    suspend fun updateProfile(name: String, interests: Set<Category>): User
    suspend fun signOut()
}

enum class AccountMode { LOCAL_DEMO, CONNECTED }

object AccountRules {
    fun name(value: String): String = value.trim().also {
        require(it.length in 2..30) { "Use a nickname of 2–30 characters." }
        require(it.none { character -> character.isISOControl() }) { "Use a nickname on one line." }
    }

    fun email(value: String): String = value.trim().also {
        require(it.length <= 254 && Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(it)) {
            "Enter a valid email address."
        }
    }

    fun password(value: String, creating: Boolean = false) {
        require(value.isNotEmpty()) { "Enter your password." }
        if (creating) require(value.length >= 8) { "Use at least 8 characters for your password." }
    }
}
