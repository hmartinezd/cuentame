package com.venkoi.cuentame.core.backup

object BackupApplicationIdentity {
    const val CURRENT_APPLICATION_ID = "com.venkoi.cuentame"
    const val LEGACY_MIARA_APPLICATION_ID = "com.miara.cuentame"

    val acceptedApplicationIds: Set<String> = setOf(
        CURRENT_APPLICATION_ID,
        LEGACY_MIARA_APPLICATION_ID
    )

    fun isAccepted(applicationId: String): Boolean = applicationId in acceptedApplicationIds
}
