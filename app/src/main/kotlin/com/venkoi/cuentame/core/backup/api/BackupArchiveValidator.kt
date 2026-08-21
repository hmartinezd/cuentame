package com.venkoi.cuentame.core.backup.api

import com.venkoi.cuentame.core.model.backup.BackupValidationResult
import java.io.InputStream

interface BackupArchiveValidator {
    fun validate(inputStream: InputStream): BackupValidationResult
}
