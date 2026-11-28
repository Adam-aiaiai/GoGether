package com.gogether.app.core.account

import com.gogether.app.core.model.*

/** Pure A-owned reducers; activity and registration records keep their stable user IDs. */
object AccountState {
    fun edit(snapshot: AppSnapshot, profile: ProfileUpdate): Pair<AppSnapshot, User> {
        require(profile.signature.trim().length <= 100) { "Use a signature of up to 100 characters." }
        val (renamed, user) = edit(snapshot, profile.name, profile.interests)
        val updated = user.copy(avatarUri = profile.avatarUri, coverUri = profile.coverUri,
            gender = profile.gender, showGender = profile.showGender && profile.gender != Gender.UNDISCLOSED,
            signature = profile.signature.trim())
        return renamed.copy(users = renamed.users.map { if (it.id == updated.id) updated else it }) to updated
    }
    fun edit(snapshot: AppSnapshot, name: String, interests: Set<Category>): Pair<AppSnapshot, User> {
        val current = requireNotNull(snapshot.currentUser) { "Sign in before editing your profile." }
        val cleanName = AccountRules.name(name)
        require(snapshot.users.none { it.id != current.id && it.displayName.equals(cleanName, true) }) {
            "That nickname is already in use. Choose another one."
        }
        val updated = current.copy(displayName = cleanName, interests = interests.toSet())
        return snapshot.copy(users = snapshot.users.map { if (it.id == updated.id) updated else it }) to updated
    }

    fun accept(snapshot: AppSnapshot, user: User): AppSnapshot {
        require(user.id.isNotBlank() && user.status == UserStatus.ACCOUNT) { "The account service returned an invalid identity." }
        AccountRules.name(user.displayName)
        require(snapshot.users.none { it.id == user.id && it.status != UserStatus.ACCOUNT }) {
            "The account identity conflicts with a local test profile."
        }
        return snapshot.copy(users = snapshot.users.filterNot { it.id == user.id } + user.copy(interests = user.interests.toSet()),
            currentUserId = user.id, sessionRestoring = false, sessionError = null)
    }
}
