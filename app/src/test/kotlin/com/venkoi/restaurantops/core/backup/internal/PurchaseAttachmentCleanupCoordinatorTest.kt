package com.venkoi.restaurantops.core.backup.internal

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.database.dao.BackupDao
import com.venkoi.restaurantops.core.database.entity.PurchaseReceiptEntity
import com.venkoi.restaurantops.core.database.entity.WasteEventEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PurchaseAttachmentCleanupCoordinatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = mockk<Context>()
    private val backupDao = mockk<BackupDao>()
    private lateinit var coordinator: PurchaseAttachmentCleanupCoordinator
    private lateinit var attachmentsDir: File

    @Before
    fun setup() {
        // Replicate Android context.filesDir structure
        val filesDir = tempFolder.newFolder("files")
        attachmentsDir = File(filesDir, "attachments")
        attachmentsDir.mkdirs()

        every { context.filesDir } returns filesDir
        coordinator = PurchaseAttachmentCleanupCoordinator(context, backupDao)
    }

    @Test
    fun `referenced files are preserved`() = runBlocking {
        // Setup files
        val subDir = File(attachmentsDir, "sub1").apply { mkdirs() }
        val file1 = File(subDir, "file1.jpg").apply { writeText("data") }
        val file2 = File(attachmentsDir, "file2.png").apply { writeText("data") }

        // Mock DB: Path in DB is relative to context.filesDir (e.g. "attachments/...")
        val path1 = "attachments/sub1/file1.jpg"
        val path2 = "attachments/file2.png"

        coEvery { backupDao.getAllPurchaseReceipts() } returns listOf(
            mockk<PurchaseReceiptEntity>().apply { every { attachmentPath } returns path1 }
        )
        coEvery { backupDao.getAllWasteEvents() } returns listOf(
            mockk<WasteEventEntity>().apply { every { attachmentPath } returns path2 }
        )

        coordinator.cleanupOrphans()

        assertThat(file1.exists()).isTrue()
        assertThat(file2.exists()).isTrue()
    }

    @Test
    fun `orphaned files are deleted`() = runBlocking {
        val subDir = File(attachmentsDir, "sub2").apply { mkdirs() }
        val orphan = File(subDir, "orphan.jpg").apply { writeText("data") }

        coEvery { backupDao.getAllPurchaseReceipts() } returns emptyList()
        coEvery { backupDao.getAllWasteEvents() } returns emptyList()

        coordinator.cleanupOrphans()

        assertThat(orphan.exists()).isFalse()
    }

    @Test
    fun `empty directories are cleaned up recursively except root and direct children`() = runBlocking {
        // attachments/
        //   category/        <- dir == rootDir, so category won't be deleted even if empty
        //     empty_nested/  <- dir == category, so empty_nested WILL be deleted if empty
        //     file_to_del.jpg
        
        val categoryDir = File(attachmentsDir, "category").apply { mkdirs() }
        val emptyNested = File(categoryDir, "empty_nested").apply { mkdirs() }
        val fileToDel = File(categoryDir, "file_to_del.jpg").apply { writeText("data") }

        coEvery { backupDao.getAllPurchaseReceipts() } returns emptyList()
        coEvery { backupDao.getAllWasteEvents() } returns emptyList()

        coordinator.cleanupOrphans()

        assertThat(emptyNested.exists()).isFalse()
        assertThat(fileToDel.exists()).isFalse()
        // Direct children of rootDir are preserved as per 'dir != rootDir' check
        assertThat(categoryDir.exists()).isTrue()
        assertThat(attachmentsDir.exists()).isTrue()
    }

    @Test
    fun `deeply nested empty directories are removed`() = runBlocking {
        // attachments/category/nested/very_nested/
        val categoryDir = File(attachmentsDir, "category").apply { mkdirs() }
        val nestedDir = File(categoryDir, "nested").apply { mkdirs() }
        val veryNestedDir = File(nestedDir, "very_nested").apply { mkdirs() }

        coEvery { backupDao.getAllPurchaseReceipts() } returns emptyList()
        coEvery { backupDao.getAllWasteEvents() } returns emptyList()

        coordinator.cleanupOrphans()

        assertThat(veryNestedDir.exists()).isFalse()
        assertThat(nestedDir.exists()).isFalse()
        assertThat(categoryDir.exists()).isTrue() 
    }

    @Test
    fun `temporary files are preserved even if orphaned`() = runBlocking {
        val tmpFile = File(attachmentsDir, "active.tmp").apply { writeText("data") }

        coEvery { backupDao.getAllPurchaseReceipts() } returns emptyList()
        coEvery { backupDao.getAllWasteEvents() } returns emptyList()

        coordinator.cleanupOrphans()

        assertThat(tmpFile.exists()).isTrue()
    }
}
