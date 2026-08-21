package com.venkoi.restaurantops.core.backup.api

import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Factory for creating [ZipInputStream] wrappers.
 * Used as a narrow test seam to verify finalization behavior and fault injection.
 */
fun interface BackupZipInputFactory {
    fun create(input: InputStream): ZipInputStream
}
