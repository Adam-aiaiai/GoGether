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
            FakeGogetherRepository(initial = store.load(), store = store)
        } catch (_: Exception) {
            // Preserve an unreadable save instead of overwriting it with fresh demo data.
            FakeGogetherRepository(storageDescription =
                "Saved data could not be opened. This temporary session will not overwrite it.")
        }
    }
}
