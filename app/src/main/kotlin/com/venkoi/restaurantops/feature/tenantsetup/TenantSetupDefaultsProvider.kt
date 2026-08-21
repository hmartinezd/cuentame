package com.venkoi.restaurantops.feature.tenantsetup

import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

interface TenantSetupDefaultsProvider {
    fun timezone(): String
    fun localeTag(): String
}

class AndroidTenantSetupDefaultsProvider @Inject constructor() : TenantSetupDefaultsProvider {
    override fun timezone(): String = TimeZone.getDefault().id

    override fun localeTag(): String =
        AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag()
            ?.takeIf { it.isNotBlank() }
            ?: Locale.getDefault().toLanguageTag().takeIf { it.isNotBlank() }
            ?: "en-US"
}
