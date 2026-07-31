package com.miara.cuentame.core.presentation.navigation

/**
 * Platform-independent route segment encoder.
 */
interface RouteEncoder {
    fun encode(s: String): String
}

/**
 * Default implementation for production (Android).
 */
object AndroidRouteEncoder : RouteEncoder {
    override fun encode(s: String): String = android.net.Uri.encode(s)
}
