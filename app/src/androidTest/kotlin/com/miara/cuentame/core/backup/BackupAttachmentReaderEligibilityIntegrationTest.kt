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
    fun attachmentBearingArchive_fixtureIsStructurallyValidButRejected() = runBlocking {
        val fixture = BackupTestFixtures.createValidAttachmentArchiveFixture(jsonCodecs)
        val docUri = BackupDocumentUri("content://test/attachment.zip")
        
        // 1. Reader layer
        val readerResult = archiveReader.inspect(ByteArrayInputStream(fixture.archiveBytes), docUri)
        assertThat(readerResult).isInstanceOf(BackupArchiveInspectionResult.Ready::class.java)
        
        val ready = readerResult as BackupArchiveInspectionResult.Ready
        assertThat(ready.eligibility).isEqualTo(BackupRestoreEligibility.AttachmentsNotSupported)
        
        // Prove it reaches eligibility check by passing manifest and checksum validation
        assertThat(ready.archive.manifest.attachments).isNotEmpty()
        assertThat(ready.archive.manifest.attachments[0].attachmentId).isEqualTo(fixture.attachmentId)
    }
}

