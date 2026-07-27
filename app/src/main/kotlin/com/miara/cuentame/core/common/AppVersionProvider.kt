package com.miara.cuentame.core.common

interface AppVersionProvider {
    val applicationId: String
    val versionName: String
    val versionCode: Long
    val databaseSchemaVersion: Int
}
