package com.miara.cuentame.core.database.repository

interface IntegrationFailureBoundary {
    fun trigger(point: String)
}

class NoOpFailureBoundary : IntegrationFailureBoundary {
    override fun trigger(point: String) {}
}

class ConfigurableFailureBoundary : IntegrationFailureBoundary {
    var failurePoint: String? = null
    override fun trigger(point: String) {
        if (point == failurePoint) throw ForcedFailureException()
    }
}

class ForcedFailureException : RuntimeException("Forced integration failure")
