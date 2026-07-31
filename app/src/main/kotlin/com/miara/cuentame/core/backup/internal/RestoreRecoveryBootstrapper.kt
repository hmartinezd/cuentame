package com.miara.cuentame.core.backup.internal

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestoreRecoveryBootstrapper @Inject constructor(
    private val recoveryCoordinator: RestoreRecoveryCoordinator
) {
    private val scope = MainScope()

    fun bootstrap() {
        scope.launch {
            recoveryCoordinator.recoverIfNeeded()
        }
    }
}
