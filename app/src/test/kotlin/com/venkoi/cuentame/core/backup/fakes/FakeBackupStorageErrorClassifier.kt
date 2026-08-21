package com.venkoi.cuentame.core.backup.fakes

import com.venkoi.cuentame.core.backup.api.BackupStorageFailure
import com.venkoi.cuentame.core.backup.platform.BackupStorageErrorClassifier

class FakeBackupStorageErrorClassifier : BackupStorageErrorClassifier {
    var result: BackupStorageFailure = BackupStorageFailure.GenericIo

    override fun classify(throwable: Throwable): BackupStorageFailure {
        return result
    }
}
