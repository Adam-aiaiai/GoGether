package com.gogether.app.core.data

import com.gogether.app.core.model.AppSnapshot

/** Only local demo data. A connected backend must persist and authorize its own records. */
interface SnapshotStore {
    fun load(): AppSnapshot?
    fun save(snapshot: AppSnapshot)
}
