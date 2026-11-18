package com.gogether.app

import android.app.Application
import com.gogether.app.core.data.FakeGogetherRepository
import com.gogether.app.core.data.GogetherRepository
import com.gogether.app.core.data.DemoData
import com.gogether.app.core.data.FileSnapshotStore
import java.io.File

class GogetherApplication : Application() {
    val repository: GogetherRepository by lazy {
        val store = FileSnapshotStore(File(filesDir, "demo-snapshot-v1.json"))
        try {
            FakeGogetherRepository(initial = store.load() ?: DemoData.create(), store = store)
        } catch (_: Exception) {
            // Preserve an unreadable save instead of overwriting it with fresh demo data.
            FakeGogetherRepository(storageDescription =
                "Saved data could not be opened. This temporary session will not overwrite it.")
        }
    }
}
