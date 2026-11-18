package com.gogether.app.core.data

import android.util.AtomicFile
import com.gogether.app.core.model.AppSnapshot
import java.io.File
import java.io.FileNotFoundException

class FileSnapshotStore(file: File) : SnapshotStore {
    private val atomicFile = AtomicFile(file)
    override fun load(): AppSnapshot? = try {
        atomicFile.openRead().bufferedReader(Charsets.UTF_8).use { SnapshotJson.decode(it.readText()) }
    } catch (_: FileNotFoundException) { null }

    override fun save(snapshot: AppSnapshot) {
        val bytes = SnapshotJson.encode(snapshot).toByteArray(Charsets.UTF_8)
        val output = atomicFile.startWrite()
        try {
            output.write(bytes)
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            atomicFile.failWrite(output)
            throw error
        }
    }
}
