package com.miara.cuentame.core.database.repository

class ConfigurableFailureBoundary : IntegrationFailureBoundary {
    var failurePoint: String? = null
    var triggerCount: Int = 0
        private set

    override fun trigger(point: String) {
        if (point == failurePoint) {
            triggerCount++
            throw ForcedFailureException()
        }
    }

    fun triggerOn(point: String) {
        this.failurePoint = point
    }

    fun reset() {
        failurePoint = null
        triggerCount = 0
    }
}

class ForcedFailureException : RuntimeException("Forced integration failure")
