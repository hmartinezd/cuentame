package com.venkoi.restaurantops.core.common

import android.content.Context
import com.venkoi.restaurantops.BuildConfig
import com.venkoi.restaurantops.core.common.database.DatabaseSchema
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAppVersionProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : AppVersionProvider {
    override val applicationId: String = BuildConfig.APPLICATION_ID
    override val versionName: String = BuildConfig.VERSION_NAME
    override val versionCode: Long = BuildConfig.VERSION_CODE.toLong()
    override val databaseSchemaVersion: Int = DatabaseSchema.VERSION
}
