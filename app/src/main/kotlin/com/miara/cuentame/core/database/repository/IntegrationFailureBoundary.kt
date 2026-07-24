package com.miara.cuentame.core.database.repository

interface IntegrationFailureBoundary {
    fun trigger(point: String)
}

class NoOpFailureBoundary : IntegrationFailureBoundary {
    override fun trigger(point: String) {}
}

class ForcedFailureException : RuntimeException("Forced integration failure")
