package com.miara.cuentame.core.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.api.*
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.model.backup.BackupRestoreEligibility
import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BackupAttachmentReaderEligibilityIntegrationTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var archiveReader: BackupArchiveReader

    @Inject
    lateinit var jsonCodecs: BackupJsonCodecs

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun attachmentBearingV1Archive_isRejectedEarlyByManifestMismatch() = runBlocking {
        val fixture = BackupTestFixtures.createValidAttachmentArchiveFixture(jsonCodecs)
        val docUri = BackupDocumentUri("content://test/attachment.zip")
        
        // 1. Reader layer catches it via validateManifestStructure
        val readerResult = archiveReader.inspect(ByteArrayInputStream(fixture.archiveBytes), docUri)
        assertThat(readerResult).isInstanceOf(BackupArchiveInspectionResult.Failure::class.java)
        
        val failure = readerResult as BackupArchiveInspectionResult.Failure
        assertThat(failure.reason).isEqualTo(BackupRestoreFailure.ManifestMismatch)
    }
}

