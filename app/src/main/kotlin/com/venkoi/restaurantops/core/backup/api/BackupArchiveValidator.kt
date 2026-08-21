package com.venkoi.restaurantops.core.backup.api

import com.venkoi.restaurantops.core.model.backup.BackupValidationResult
import java.io.InputStream

interface BackupArchiveValidator {
    fun validate(inputStream: InputStream): BackupValidationResult
}
