package com.miara.cuentame.core.backup.fakes

import com.miara.cuentame.core.backup.api.BackupStorageFailure
import com.miara.cuentame.core.backup.platform.BackupStorageErrorClassifier

class FakeBackupStorageErrorClassifier : BackupStorageErrorClassifier {
    var nextFailure: BackupStorageFailure = BackupStorageFailure.GenericIo

    override fun classify(throwable: Throwable): BackupStorageFailure {
        return nextFailure
    }
}
