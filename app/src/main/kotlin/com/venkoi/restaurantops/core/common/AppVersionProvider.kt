package com.venkoi.restaurantops.core.common

interface AppVersionProvider {
    val applicationId: String
    val versionName: String
    val versionCode: Long
    val databaseSchemaVersion: Int
}
