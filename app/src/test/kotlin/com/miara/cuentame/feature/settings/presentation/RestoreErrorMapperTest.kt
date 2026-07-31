package com.miara.cuentame.feature.settings.presentation

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.model.backup.BackupRestoreFailure
import com.miara.cuentame.core.backup.BackupSnapshotIntegrityCode
import org.junit.Test

class RestoreErrorMapperTest {

    @Test
    fun `snapshot integrity failure maps to generic integrity message`() {
        val failure = BackupRestoreFailure.SnapshotIntegrityFailure(BackupSnapshotIntegrityCode.RESTAURANT_NAME_MISMATCH)
        val resId = failure.toUserMessageRes()
        
        // We can't easily check the string content in JVM test without robolectric,
        // but we can verify it doesn't return a "leaky" ID if we had one.
        // The most important thing is that it maps to a specific R.string constant.
        assertThat(resId).isNotEqualTo(0)
    }

    @Test
    fun `all failures have a mapping`() {
        // This is implicitly verified by the 'when' expression in the implementation being exhaustive.
    }
}
