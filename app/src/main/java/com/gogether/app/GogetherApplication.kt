package com.gogether.app

import android.app.Application
import com.gogether.app.core.data.FakeGogetherRepository
import com.gogether.app.core.data.GogetherRepository
import com.gogether.app.core.data.FileSnapshotStore
import com.gogether.app.core.data.IntegratedSnapshotStore
import com.gogether.app.core.data.LocalSnapshotStore
import java.io.File
import com.gogether.app.core.data.cloud.FirebaseGogetherRepository

class GogetherApplication : Application() {
    val repository: GogetherRepository by lazy {
        if (BuildConfig.BACKEND_MODE != "demo") return@lazy FirebaseGogetherRepository(this)
        val store = IntegratedSnapshotStore(
            FileSnapshotStore(File(filesDir, "integrated-snapshot-v1.json")),
            FileSnapshotStore(File(filesDir, "demo-snapshot-v1.json")),
            LocalSnapshotStore(this)::load,
        )
        try {
            val social = com.gogether.app.feature.social.LocalSocialStore(this)
            val loaded = store.load()
            val maintainerId = com.gogether.app.feature.social.LocalSocialStore.MAINTAINER
            val initial = if (loaded.users.any { it.id == maintainerId }) loaded else loaded.copy(users = loaded.users +
                com.gogether.app.core.model.User(maintainerId, "Project maintainer (Demo)", emptySet(), System.currentTimeMillis()))
            FakeGogetherRepository(initial = initial, store = store,
                registrationBlocked = { host, user -> social.load().registrationBlocked(host, user) },
                interactionBlocked = { a, b -> social.load().blocked(a, b) }, contentDeleted = { it in social.load().deleted })
        } catch (_: Exception) {
            // Preserve an unreadable save instead of overwriting it with fresh demo data.
            FakeGogetherRepository(storageDescription =
                "Saved data could not be opened. This temporary session will not overwrite it.")
        }
    }
}
