package com.miara.cuentame.core.common.database

/**
 * Single authoritative source for the Room database schema version.
 * Used by RestaurantInventoryDatabase and AndroidAppVersionProvider
 * to ensure backup manifests and Room migrations cannot drift silently.
 */
object DatabaseSchema {
    const val VERSION = 8
}
