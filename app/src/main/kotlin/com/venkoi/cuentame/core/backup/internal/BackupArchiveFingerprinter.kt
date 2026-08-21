package com.venkoi.cuentame.core.backup.internal

import com.venkoi.cuentame.core.backup.api.BackupArchiveFingerprint
import com.venkoi.cuentame.core.backup.api.BackupJsonCodecs
import com.venkoi.cuentame.core.model.backup.BackupManifest
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupArchiveFingerprinter @Inject constructor(
    private val codecs: BackupJsonCodecs
) {
    /**
     * Calculates a stable fingerprint over the logical archive content.
     */
    fun calculate(
        manifest: BackupManifest,
        checksums: Map<String, String>
    ): BackupArchiveFingerprint {
        val digest = MessageDigest.getInstance("SHA-256")
        
        // 1. Manifest (canonical representation)
        val manifestJson = codecs.writer.encodeToString(BackupManifest.serializer(), manifest)
        digest.update(manifestJson.toByteArray())
        
        // 2. Checksums in deterministic order
        checksums.toSortedMap().forEach { (k, v) ->
            digest.update(k.toByteArray())
            digest.update(v.toByteArray())
        }
        
        // 3. Versions
        digest.update(manifest.backupFormatVersion.toString().toByteArray())
        digest.update(manifest.databaseSchemaVersion.toString().toByteArray())
        
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        return BackupArchiveFingerprint(hash)
    }
}
