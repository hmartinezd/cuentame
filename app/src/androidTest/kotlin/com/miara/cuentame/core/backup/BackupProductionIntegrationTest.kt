package com.miara.cuentame.core.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.domain.repository.BackupRepository
import com.miara.cuentame.core.model.backup.BackupValidationResult
import com.miara.cuentame.test.TestSeeder
import com.miara.cuentame.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import javax.inject.Inject

@HiltAndroidTest
class BackupProductionIntegrationTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var backupRepository: BackupRepository

    @Inject
    lateinit var testStateManager: TestStateManager

    @Before
    fun init() {
        hiltRule.inject()
        runBlocking { testStateManager.resetAll() }
    }

    @After
    fun tearDown() {
        runBlocking { testStateManager.resetAll() }
    }

    @Test
    fun fullBackupPipeline_producesValidArchive() = runTest {
        testStateManager.seedBaseline()
        
        val context = ApplicationProvider.getApplicationContext<Context>()
        val backupFile = File(context.cacheDir, "integration_test.zip")
        if (backupFile.exists()) backupFile.delete()
        
        val destinationUri = "file://${backupFile.absolutePath}"
        
        val results = backupRepository.createBackup(destinationUri).toList()
        
        assertThat(results).containsExactly(
            BackupOperationStatus.Creating,
            BackupOperationStatus.Validating,
            results.last { it is BackupOperationStatus.Success }
        ).inOrder()
        
        assertThat(backupFile.exists()).isTrue()
        assertThat(backupFile.length()).isGreaterThan(0)
        
        val validation = backupRepository.validateBackup(destinationUri)
        assertThat(validation).isInstanceOf(BackupValidationResult.Valid::class.java)
        
        val valid = validation as BackupValidationResult.Valid
        assertThat(valid.manifest.restaurantId).isEqualTo(TestSeeder.RESTAURANT_ID)
    }
}
