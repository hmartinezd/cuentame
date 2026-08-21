package com.venkoi.restaurantops.core.device

import android.content.Context
import com.venkoi.restaurantops.core.common.ids.IdGenerator
import com.venkoi.restaurantops.core.domain.service.InstallationIdProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidInstallationIdProvider @Inject constructor(
    @ApplicationContext context: Context,
    private val idGenerator: IdGenerator
) : InstallationIdProvider {

    private val preferences = context.getSharedPreferences(
        INSTALLATION_PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override suspend fun getOrCreateInstallationId(): String = synchronized(lock) {
        preferences.getString(INSTALLATION_ID_KEY, null) ?: idGenerator.newId().also { newId ->
            check(preferences.edit().putString(INSTALLATION_ID_KEY, newId).commit()) {
                "Unable to persist installation identity"
            }
        }
    }

    private companion object {
        const val INSTALLATION_PREFERENCES_NAME = "device_installation_identity"
        const val INSTALLATION_ID_KEY = "installation_id"
        val lock = Any()
    }
}
