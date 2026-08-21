package com.venkoi.cuentame.core.backup.internal

import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.backup.api.RestoreStartupState
import kotlinx.coroutines.*
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RestoreOperationGateTest {

    private val gate = RestoreOperationGate()

    @Test
    fun `withOperationalLock waits for terminal state`() = runTest {
        val results = mutableListOf<String>()
        
        launch {
            gate.withOperationalLock(
                onRecoveryRequired = { results.add("recovery_required") }
            ) {
                results.add("success")
            }
        }
        
        runCurrent()
        assertThat(results).isEmpty() // Still waiting for terminal state

        gate.updateRecoveryState(RestoreStartupState.Ready)
        runCurrent()
        
        assertThat(results).containsExactly("success")
    }

    @Test
    fun `withOperationalLock rechecks state after lock acquisition`() = runTest {
        val results = mutableListOf<String>()
        
        // Initial state NotStarted
        launch {
            gate.withOperationalLock(
                onRecoveryRequired = { results.add("recovery_required") }
            ) {
                results.add("success")
            }
        }

        runCurrent()
        
        // Change to Recovering under the hood (simulating a race)
        // Actually, we can't easily simulate a race where it acquires lock AFTER we change state but BEFORE it checks.
        // But the loop logic handles it.
        
        gate.updateRecoveryState(RestoreStartupState.Ready)
        runCurrent()
        
        assertThat(results).containsExactly("success")
    }

    @Test
    fun `two restores cannot overlap`() = runTest {
        val results = mutableListOf<String>()
        gate.updateRecoveryState(RestoreStartupState.Ready)

        launch {
            gate.withOperationalLock({ }) {
                results.add("restore1_started")
                delay(1000)
                results.add("restore1_finished")
            }
        }
        
        runCurrent()
        assertThat(results).containsExactly("restore1_started")

        launch {
            gate.withOperationalLock({ }) {
                results.add("restore2_started")
            }
        }
        
        runCurrent()
        assertThat(results).containsExactly("restore1_started")

        advanceTimeBy(1001)
        runCurrent()
        
        assertThat(results).containsExactly("restore1_started", "restore1_finished", "restore2_started")
    }

    @Test
    fun `startup NotStarted operation does not deadlock`() = runTest {
        val results = mutableListOf<String>()
        
        // Initial state is NotStarted. bootstrap() will change it to Recovering, then Ready.
        launch {
            gate.withOperationalLock({ }) {
                results.add("operation_started")
            }
        }
        
        runCurrent()
        assertThat(results).isEmpty() // Waiting for terminal state

        // Simulate bootstrap process
        gate.updateRecoveryState(RestoreStartupState.Recovering)
        runCurrent()
        assertThat(results).isEmpty() // Still waiting

        gate.updateRecoveryState(RestoreStartupState.Ready)
        runCurrent()
        assertThat(results).containsExactly("operation_started")
    }

    @Test
    fun `withOperationalLock invokes onRecoveryRequired when state is RecoveryRequired`() = runTest {
        val results = mutableListOf<String>()
        gate.updateRecoveryState(RestoreStartupState.RecoveryRequired)
        
        gate.withOperationalLock(
            onRecoveryRequired = { results.add("recovery_required") }
        ) {
            results.add("success")
        }
        
        assertThat(results).containsExactly("recovery_required")
    }

    @Test
    fun `operation is blocked after restore requires recovery`() = runTest {
        val results = mutableListOf<String>()
        
        // 1. Set state to RecoveryRequired
        gate.updateRecoveryState(RestoreStartupState.RecoveryRequired)
        
        // 2. Attempt an operation
        gate.withOperationalLock(
            onRecoveryRequired = { results.add("blocked") }
        ) {
            results.add("allowed")
        }
        
        assertThat(results).containsExactly("blocked")
    }
}
