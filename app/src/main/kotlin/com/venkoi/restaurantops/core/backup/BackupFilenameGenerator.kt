package com.venkoi.restaurantops.core.backup

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object BackupFilenameGenerator {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")
        .withZone(ZoneOffset.UTC)

    fun generate(restaurantName: String?, timestamp: Instant): String {
        val sanitizedName = restaurantName
            ?.replace(Regex("[^a-zA-Z0-9]"), "_")
            ?.replace(Regex("_+"), "_")
            ?.trim('_')
            ?.ifBlank { null }
            ?: "Backup"

        val formattedDate = formatter.format(timestamp)
        return "Cuentame_${sanitizedName}_$formattedDate.cuentame-backup"
    }
}
