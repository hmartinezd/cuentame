package com.venkoi.restaurantops.core.domain.service

interface InstallationIdProvider {
    suspend fun getOrCreateInstallationId(): String
}
