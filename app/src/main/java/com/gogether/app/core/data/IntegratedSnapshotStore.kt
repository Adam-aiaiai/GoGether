package com.gogether.app.core.data

import com.gogether.app.core.model.AppSnapshot

/** Migrate one installed application's data without replacing event instances or E's private keys. */
class IntegratedSnapshotStore(
    private val destination: SnapshotStore,
    private val abd: SnapshotStore,
    private val ce: () -> AppSnapshot?,
) : SnapshotStore {
    override fun load(): AppSnapshot {
        destination.load()?.let { return it }
        val oldAbd = abd.load()
        val oldCe = ce()
        require(oldAbd == null || oldCe == null || oldAbd.copy(currentUserId = null) == oldCe.copy(currentUserId = null)) {
            "Two different saved datasets were found. Keep both files and choose which dataset to migrate."
        }
        val initial = oldAbd ?: oldCe ?: DemoData.create()
        destination.save(initial)
        return initial
    }
    override fun save(snapshot: AppSnapshot) = destination.save(snapshot)
}
