package com.venkoi.restaurantops.core.backup.api

sealed interface BackupStorageFailure {
    data object InsufficientSpace : BackupStorageFailure
    data object PermissionDenied : BackupStorageFailure
    data object DestinationUnavailable : BackupStorageFailure
    data object GenericIo : BackupStorageFailure
}
