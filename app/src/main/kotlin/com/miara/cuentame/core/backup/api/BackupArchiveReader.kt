package com.miara.cuentame.core.backup.api

import java.io.InputStream

/**
 * Strategy for reading and inspecting a backup archive from a stream.
 */
interface BackupArchiveReader {
    /**
     * Inspects the provided stream for backup validity and content.
     * The reader must not close the provided [input] stream.
     */
    suspend fun inspect(
        input: InputStream,
        source: BackupDocumentUri
    ): BackupArchiveInspectionResult
}
