package com.venkoi.restaurantops.core.backup.internal

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic failure injection checkpoints for restore and recovery tests.
 */
enum class RestoreCheckpoint {
    BEFORE_MUTATION,
    AFTER_LIVE_ATTACHMENTS_INSTALLED,
    AFTER_DATABASE_APPLIED,
    AFTER_PREFERENCES_APPLIED,
    BEFORE_FINAL_VERIFICATION
}

/**
 * Internal interface for injecting failures during the restore process.
 * Production implementation should be a no-op.
 */
interface RestoreFailureInjector {
    fun onCheckpoint(checkpoint: RestoreCheckpoint)
    fun injectCancellation(checkpoint: RestoreCheckpoint)
}

/**
 * Production implementation that does nothing.
 */
@Singleton
class NoOpRestoreFailureInjector @Inject constructor() : RestoreFailureInjector {
    override fun onCheckpoint(checkpoint: RestoreCheckpoint) = Unit
    override fun injectCancellation(checkpoint: RestoreCheckpoint) = Unit
}
