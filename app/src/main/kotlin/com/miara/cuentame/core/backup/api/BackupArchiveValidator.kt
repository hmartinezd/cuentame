package com.miara.cuentame.core.backup.api

import com.miara.cuentame.core.model.backup.BackupValidationResult
import java.io.InputStream

interface BackupArchiveValidator {
    fun validate(inputStream: InputStream): BackupValidationResult
}
