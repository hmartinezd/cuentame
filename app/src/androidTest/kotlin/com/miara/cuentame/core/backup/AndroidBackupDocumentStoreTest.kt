package com.miara.cuentame.core.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.BackupDocumentOperation
import com.miara.cuentame.core.backup.api.BackupDocumentUri
import com.miara.cuentame.core.backup.api.BackupDocumentOpenException
import com.miara.cuentame.core.backup.platform.AndroidBackupDocumentStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

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
    fun truncate_validFile_emptiesFile() = runBlocking {
        val file = File(context.cacheDir, "to_truncate.zip")
        file.writeText("some content")
        val uri = BackupDocumentUri("file://${file.absolutePath}")
        
        val truncated = store.truncate(uri)
        assertThat(truncated).isTrue()
        assertThat(file.length()).isEqualTo(0)
    }
}
