package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.model.backup.BackupAttachmentReference
import org.junit.Assert.assertThrows
import org.junit.Test

class PlannedBackupAttachmentTest {

    private val validId = "0123456789abcdef"
    private val validUri = AttachmentSourceUri("content://img")
    private val validName = "photo.jpg"
    private val validPath = "attachments/$validId/$validName"
    private val validChecksum = "a".repeat(64)
    private val validRef = BackupAttachmentReference("WASTE_EVENT", "w1")

    @Test
    fun `create with valid data succeeds`() {
        val att = PlannedBackupAttachment.create(
            sourceUri = validUri,
            attachmentId = validId,
            archivePath = validPath,
            displayName = validName,
            mimeType = "image/jpeg",
            sizeBytes = 100L,
            checksumSha256 = validChecksum,
            references = listOf(validRef)
        )
        assertThat(att.attachmentId).isEqualTo(validId)
    }

    @Test
    fun `create rejects invalid attachment ID`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlannedBackupAttachment.create(
                validUri, "invalid-id", validPath, validName, null, 100L, validChecksum, listOf(validRef)
            )
        }
    }

    @Test
    fun `create rejects non-canonical archive path`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlannedBackupAttachment.create(
                validUri, validId, "wrong/path/file.jpg", validName, null, 100L, validChecksum, listOf(validRef)
            )
        }
    }

    @Test
    fun `create rejects unsafe archive path`() {
        assertThrows(IllegalArgumentException::class.java) {
            val unsafeName = "../../etc/passwd"
            val unsafePath = "attachments/$validId/$unsafeName"
            PlannedBackupAttachment.create(
                validUri, validId, unsafePath, unsafeName, null, 100L, validChecksum, listOf(validRef)
            )
        }
    }

    @Test
    fun `create rejects invalid display name`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlannedBackupAttachment.create(
                validUri, validId, validPath, "   ", null, 100L, validChecksum, listOf(validRef)
            )
        }
    }

    @Test
    fun `create rejects invalid checksum`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlannedBackupAttachment.create(
                validUri, validId, validPath, validName, null, 100L, "short", listOf(validRef)
            )
        }
    }

    @Test
    fun `create rejects duplicate references`() {
        val dupRef = BackupAttachmentReference("WASTE_EVENT", "w1")
        assertThrows(IllegalArgumentException::class.java) {
            PlannedBackupAttachment.create(
                validUri, validId, validPath, validName, null, 100L, validChecksum, listOf(validRef, dupRef)
            )
        }
    }

    @Test
    fun `create rejects empty references`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlannedBackupAttachment.create(
                validUri, validId, validPath, validName, null, 100L, validChecksum, emptyList()
            )
        }
    }
    
    @Test
    fun `create rejects unsupported record type`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlannedBackupAttachment.create(
                validUri, validId, validPath, validName, null, 100L, validChecksum, 
                listOf(BackupAttachmentReference("INVALID_TYPE", "r1"))
            )
        }
    }
}
