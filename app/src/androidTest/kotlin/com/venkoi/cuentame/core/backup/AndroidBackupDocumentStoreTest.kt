package com.venkoi.cuentame.core.backup

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.backup.api.BackupDocumentOperation
import com.venkoi.cuentame.core.backup.api.BackupDocumentUri
import com.venkoi.cuentame.core.backup.api.BackupDocumentOpenException
import com.venkoi.cuentame.core.backup.platform.AndroidBackupDocumentStore
import com.venkoi.cuentame.core.backup.platform.ParcelFileDescriptorStreamFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

@RunWith(AndroidJUnit4::class)
class AndroidBackupDocumentStoreTest {

    private lateinit var context: Context
    private lateinit var store: AndroidBackupDocumentStore

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        store = AndroidBackupDocumentStore(context)
    }

    @Test
    fun openForWrite_validFileUri_returnsStream() = runBlocking {
        val file = File(context.cacheDir, "test_write.zip")
        val uri = BackupDocumentUri("file://${file.absolutePath}")
        
        store.openForWrite(uri).use { 
            it.write("data".toByteArray())
        }
        
        assertThat(file.exists()).isTrue()
        assertThat(file.readText()).isEqualTo("data")
    }

    @Test
    fun openForRead_validFileUri_returnsStream() = runBlocking {
        val file = File(context.cacheDir, "test_read.zip")
        file.writeText("content")
        val uri = BackupDocumentUri("file://${file.absolutePath}")
        
        val text = store.openForRead(uri).use { 
            it.readBytes().decodeToString()
        }
        
        assertThat(text).isEqualTo("content")
    }

    @Test
    fun openForRead_nonExistent_throwsWrapped() {
        val uri = BackupDocumentUri("file:///non/existent/file.zip")
        val ex = assertThrows(BackupDocumentOpenException::class.java) {
            runBlocking { store.openForRead(uri) }
        }
        assertThat(ex.operation).isEqualTo(BackupDocumentOperation.READ)
        assertThat(ex.message).doesNotContain("/non/existent/file.zip")
    }

    @Test
    fun truncate_validFile_emptiesFile() = runBlocking {
        val file = File(context.cacheDir, "to_truncate.zip")
        file.writeText("some content")
        val uri = BackupDocumentUri("file://${file.absolutePath}")
        
        val truncated = store.truncate(uri)
        assertThat(truncated).isTrue()
        assertThat(file.length()).isEqualTo(0)
    }

    @Test
    fun closeSuppressing_attachesFailure() {
        val mockPfd = mockk<ParcelFileDescriptor>()
        every { mockPfd.close() } throws IOException("Close failed")
        
        val primary = RuntimeException("Primary")
        store.closeSuppressing(mockPfd, primary)
        
        assertThat(primary.suppressed).hasLength(1)
        assertThat(primary.suppressed[0].message).isEqualTo("Close failed")
    }

    @Test
    fun closeSuppressing_rethrowsFatal() {
        val mockPfd = mockk<ParcelFileDescriptor>()
        every { mockPfd.close() } throws OutOfMemoryError("Fatal")
        
        val primary = RuntimeException("Primary")
        assertThrows(OutOfMemoryError::class.java) {
            store.closeSuppressing(mockPfd, primary)
        }
    }

    @Test
    fun delete_validFile_returnsTrue() = runBlocking {
        val file = File(context.cacheDir, "to_delete.zip")
        file.writeText("junk")
        val uri = BackupDocumentUri("file://${file.absolutePath}")
        
        val deleted = store.delete(uri)
        assertThat(deleted).isTrue()
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun openStream_securityException_isPropagated() {
        val spyStore = spyk(store)
        every { spyStore.openDescriptor(any(), any()) } throws SecurityException("No permission")
        
        val uri = BackupDocumentUri("content://denied")
        assertThrows(SecurityException::class.java) {
            runBlocking { spyStore.openForRead(uri) }
        }
    }

    @Test
    fun openStream_nullDescriptor_throwsOpenException() {
        val spyStore = spyk(store)
        every { spyStore.openDescriptor(any(), any()) } returns null
        
        val uri = BackupDocumentUri("content://null")
        val ex = assertThrows(BackupDocumentOpenException::class.java) {
            runBlocking { spyStore.openForRead(uri) }
        }
        assertThat(ex.operation).isEqualTo(BackupDocumentOperation.READ)
    }

    @Test
    fun openStream_ioExceptionDuringConstruction_closesDescriptor() {
        val mockPfd = mockk<ParcelFileDescriptor>(relaxed = true)
        val spyStore = spyk(store)
        every { spyStore.openDescriptor(any(), any()) } returns mockPfd
        
        val failingFactory = object : ParcelFileDescriptorStreamFactory {
            override fun createInputStream(descriptor: ParcelFileDescriptor): InputStream = 
                throw IOException("Simulated failure")
            override fun createOutputStream(descriptor: ParcelFileDescriptor): OutputStream = 
                throw IOException("Simulated failure")
        }
        spyStore.streamFactory = failingFactory
        
        val uri = BackupDocumentUri("content://fail-construction")
        val ex = assertThrows(BackupDocumentOpenException::class.java) {
            runBlocking { spyStore.openForRead(uri) }
        }
        
        assertThat(ex.cause).isInstanceOf(IOException::class.java)
        assertThat(ex.cause?.message).isEqualTo("Simulated failure")
        verify(exactly = 1) { mockPfd.close() }
        assertThat(ex.message).doesNotContain("content://fail-construction")
    }

    @Test
    fun openStream_constructionFailure_andCloseFailure_keepsConstructionPrimary() {
        val mockPfd = mockk<ParcelFileDescriptor>()
        every { mockPfd.close() } throws IOException("Close failed")
        
        val spyStore = spyk(store)
        every { spyStore.openDescriptor(any(), any()) } returns mockPfd
        
        val failingFactory = object : ParcelFileDescriptorStreamFactory {
            override fun createInputStream(descriptor: ParcelFileDescriptor): InputStream = 
                throw IOException("Construction failed")
            override fun createOutputStream(descriptor: ParcelFileDescriptor): OutputStream = 
                throw IOException("Construction failed")
        }
        spyStore.streamFactory = failingFactory
        
        val uri = BackupDocumentUri("content://double-fail")
        val ex = assertThrows(BackupDocumentOpenException::class.java) {
            runBlocking { spyStore.openForRead(uri) }
        }
        
        assertThat(ex.cause?.message).isEqualTo("Construction failed")
        assertThat(ex.cause?.suppressed).hasLength(1)
        assertThat(ex.cause?.suppressed?.get(0)?.message).isEqualTo("Close failed")
    }
}
