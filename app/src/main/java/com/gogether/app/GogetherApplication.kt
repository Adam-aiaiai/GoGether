package com.gogether.app

import android.app.Application
import com.gogether.app.core.data.FakeGogetherRepository
import com.gogether.app.core.data.GogetherRepository
import com.gogether.app.core.data.LocalSnapshotStore
import com.gogether.app.core.data.DemoData

class GogetherApplication : Application() {
    val repository: GogetherRepository by lazy {
        val store = LocalSnapshotStore(this)
        val initial = store.load() ?: DemoData.create().also(store::save)
        FakeGogetherRepository(initial, save = store::save)
    }
}
