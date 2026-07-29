package com.miara.cuentame.core.backup

import com.miara.cuentame.core.backup.api.BackupJsonCodecs
import com.miara.cuentame.core.model.backup.BackupManifest
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveTestBuilder(
    private val jsonCodecs: BackupJsonCodecs = BackupJsonCodecs()
) {
    private val entries = mutableMapOf<String, ByteArray>()

    fun withEntry(name: String, content: ByteArray) = apply { entries[name] = content }
    
    fun withManifest(manifest: BackupManifest) = apply {
        withEntry("manifest.json", jsonCodecs.writer.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
    }
    
    fun withChecksums(checksums: Map<String, String>) = apply {
        val serializer = MapSerializer(String.serializer(), String.serializer())
        withEntry("checksums.json", jsonCodecs.writer.encodeToString(serializer, checksums).toByteArray())
    }

    fun build(): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(content)
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }
}
