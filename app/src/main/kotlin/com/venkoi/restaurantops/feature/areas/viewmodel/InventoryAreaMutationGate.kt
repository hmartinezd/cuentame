package com.venkoi.restaurantops.feature.areas.viewmodel

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** Coordinates accepted area mutations with the human conflict-decision window. */
@Singleton
class InventoryAreaMutationGate @Inject constructor() {
    private val monitor = Any()
    private val activeMutations = MutableStateFlow(0)
    private var conflictLocked = false

    fun tryBeginMutation(): MutationPermit? = synchronized(monitor) {
        if (conflictLocked) return@synchronized null
        activeMutations.value += 1
        MutationPermit(::finishMutation)
    }

    fun lockForConflict() = synchronized(monitor) {
        conflictLocked = true
    }

    suspend fun awaitAcceptedMutations() {
        activeMutations.first { it == 0 }
    }

    fun unlockConflict() = synchronized(monitor) {
        conflictLocked = false
    }

    private fun finishMutation() = synchronized(monitor) {
        check(activeMutations.value > 0)
        activeMutations.value -= 1
    }

    class MutationPermit internal constructor(private val finish: () -> Unit) {
        private val completed = AtomicBoolean(false)

        fun complete() {
            if (completed.compareAndSet(false, true)) finish()
        }
    }
}
