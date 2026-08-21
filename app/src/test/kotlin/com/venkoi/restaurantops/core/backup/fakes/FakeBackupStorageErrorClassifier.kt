package com.venkoi.restaurantops.core.backup.fakes

import com.venkoi.restaurantops.core.backup.api.BackupStorageFailure
import com.venkoi.restaurantops.core.backup.platform.BackupStorageErrorClassifier

class FakeBackupStorageErrorClassifier : BackupStorageErrorClassifier {
    var result: BackupStorageFailure = BackupStorageFailure.GenericIo

    override fun classify(throwable: Throwable): BackupStorageFailure {
        return result
    }
}
