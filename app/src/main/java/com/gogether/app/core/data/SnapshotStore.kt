package com.gogether.app.core.data

import com.gogether.app.core.model.AppSnapshot
import com.google.gson.Gson
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

interface SnapshotStore {
    fun load(): AppSnapshot?
    fun save(snapshot: AppSnapshot)
}

/** Private application storage, one repository writer. No silent reset of a damaged save. */
class FileSnapshotStore(private val file: File) : SnapshotStore {
    private val gson = Gson()
    private data class Save(val version: Int, val snapshot: AppSnapshot)
    override fun load(): AppSnapshot? {
        if (!file.exists()) return null
        val save = gson.fromJson(file.readText(), Save::class.java)
        require(save != null && save.version == 1) { "Unsupported or damaged local save." }
        return requireNotNull(save.snapshot)
    }
    override fun save(snapshot: AppSnapshot) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        java.io.FileOutputStream(temporary).use { stream ->
            stream.write(gson.toJson(Save(1, snapshot)).toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING)
    }
}
