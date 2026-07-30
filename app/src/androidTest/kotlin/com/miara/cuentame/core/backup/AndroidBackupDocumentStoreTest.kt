package com.miara.cuentame.core.backup

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.BackupDocumentOperation
import com.miara.cuentame.core.backup.api.BackupDocumentUri
import com.miara.cuentame.core.backup.api.BackupDocumentOpenException
import com.miara.cuentame.core.backup.platform.AndroidBackupDocumentStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

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
    fun openForRead_nonExistent_throwsWrapped() {
        val uri = BackupDocumentUri("file:///non/existent/file.zip")
        val ex = assertThrows(BackupDocumentOpenException::class.java) {
            runBlocking { store.openForRead(uri) }
        }
        assertThat(ex.operation).isEqualTo(BackupDocumentOperation.READ)
        assertThat(ex.message).doesNotContain("/non/existent/file.zip")
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
}
