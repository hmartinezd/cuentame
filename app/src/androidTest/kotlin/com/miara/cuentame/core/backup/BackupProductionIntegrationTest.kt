package com.miara.cuentame.core.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.domain.repository.BackupOperationStatus
import com.miara.cuentame.core.domain.repository.BackupRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
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

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun fullBackupPipeline_producesValidArchive() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val backupFile = File(context.cacheDir, "integration_test.zip")
        if (backupFile.exists()) backupFile.delete()
        
        val destinationUri = "file://${backupFile.absolutePath}"
        
        val results = backupRepository.createBackup(destinationUri).toList()
        
        assertThat(results.last()).isInstanceOf(BackupOperationStatus.Success::class.java)
        
        // Verify file exists and is non-empty
        assertThat(backupFile.exists()).isTrue()
        assertThat(backupFile.length()).isGreaterThan(0)
        
        val validation = backupRepository.validateBackup(destinationUri)
        assertThat(validation).isInstanceOf(com.miara.cuentame.core.model.backup.BackupValidationResult.Valid::class.java)
    }
}
