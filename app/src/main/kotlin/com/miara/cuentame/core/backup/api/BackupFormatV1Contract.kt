package com.miara.cuentame.core.backup.api

object BackupFormatV1Contract {

    const val BACKUP_FORMAT_VERSION = 1
    const val DATABASE_SCHEMA_VERSION = 2

    const val DATABASE_ENTRY = "data/database.json"
    const val PREFERENCES_ENTRY = "preferences/settings.json"
    const val MANIFEST_ENTRY = "manifest.json"
    const val CHECKSUMS_ENTRY = "checksums.json"

    val CORE_ENTRIES: Set<String> = setOf(
        DATABASE_ENTRY,
        PREFERENCES_ENTRY,
        MANIFEST_ENTRY,
        CHECKSUMS_ENTRY
    )

    private val attachmentIdRegex = Regex("^[0-9a-f]{16}$")
    private val checksumRegex = Regex("^[0-9a-f]{64}$")

    fun isValidAttachmentId(value: String): Boolean =
        attachmentIdRegex.matches(value)

    fun isValidChecksum(value: String): Boolean =
        checksumRegex.matches(value)

    fun attachmentArchivePath(attachmentId: String, displayName: String): String =
        "attachments/$attachmentId/$displayName"
}
