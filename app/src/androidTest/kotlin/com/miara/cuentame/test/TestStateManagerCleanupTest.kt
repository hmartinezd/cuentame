package com.miara.cuentame.test

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miara.cuentame.core.backup.internal.RestoreOperationGate
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TestStateManagerCleanupTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var testStateManager: TestStateManager

    @Before
    fun setup() {
        val database = mockk<RestaurantInventoryDatabase>(relaxed = true)
        val preferences = mockk<AppPreferencesRepository>(relaxed = true)
        val dataStoreOwner = mockk<TestDataStoreOwner>(relaxed = true)
        val restoreGate = RestoreOperationGate()

        testStateManager = TestStateManager(
            database = database,
            preferences = preferences,
            dataStoreOwner = dataStoreOwner,
            restoreGate = restoreGate,
            context = context
        )
    }

    @Test
    fun cleanup_removesMatchingFilesAndDirectories() {
        runBlocking {
            // Given
            val cacheDir = context.cacheDir
            val filesDir = context.filesDir

            val matchingFile = File(cacheDir, "integration_test_file.txt").apply { createNewFile() }
            val matchingDir = File(filesDir, "cuentame_test_backup_dir").apply { mkdir() }
            val nestedMatchingFile = File(matchingDir, "nested.txt").apply { createNewFile() }
            val matchingNestedDir = File(filesDir, "normal_dir/test_attachment_nested").apply {
                File(filesDir, "normal_dir").mkdirs()
                mkdir()
            }
            val normalFile = File(filesDir, "important_data.txt").apply { createNewFile() }

            assertTrue(matchingFile.exists())
            assertTrue(matchingDir.exists())
            assertTrue(nestedMatchingFile.exists())
            assertTrue(matchingNestedDir.exists())
            assertTrue(normalFile.exists())

            // When
            testStateManager.resetAll()

            // Then
            assertFalse("Matching file should be deleted", matchingFile.exists())
            assertFalse("Matching directory should be deleted", matchingDir.exists())
            assertFalse("Nested file in matching directory should be gone", nestedMatchingFile.exists())
            assertFalse("Nested matching directory should be deleted", matchingNestedDir.exists())
            assertTrue("Unrelated file should remain", normalFile.exists())

            // Cleanup the normal file
            normalFile.delete()
            File(filesDir, "normal_dir").deleteRecursively()
        }
    }

    @Test
    fun cleanup_isSafeWhenCalledRepeatedly() {
        runBlocking {
            testStateManager.resetAll()
            testStateManager.resetAll()
        }
    }
}
