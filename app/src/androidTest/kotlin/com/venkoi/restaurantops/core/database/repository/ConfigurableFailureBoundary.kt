package com.venkoi.restaurantops.core.database.repository

class ConfigurableFailureBoundary : IntegrationFailureBoundary {
    var failurePoint: String? = null
    private var failure: Throwable = ForcedFailureException()
    var triggerCount: Int = 0
        private set

    override fun trigger(point: String) {
        if (point == failurePoint) {
            triggerCount++
            throw failure
        }
    }

    fun triggerOn(point: String, failure: Throwable = ForcedFailureException()) {
        this.failurePoint = point
        this.failure = failure
    }

    fun reset() {
        failurePoint = null
        failure = ForcedFailureException()
        triggerCount = 0
    }
}

class ForcedFailureException : RuntimeException("Forced integration failure")
