package com.venkoi.restaurantops.feature.areas.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.app.ui.theme.AppTheme
import com.venkoi.restaurantops.core.common.ids.InventoryAreaId
import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.database.sync.InventoryAreaConflictPreview
import com.venkoi.restaurantops.core.database.sync.InventoryAreaConflictRef
import com.venkoi.restaurantops.core.model.inventory.InventoryArea
import com.venkoi.restaurantops.feature.areas.viewmodel.AreaManagementUiState
import com.venkoi.restaurantops.feature.areas.viewmodel.InventoryAreaConflictMessage
import com.venkoi.restaurantops.feature.areas.viewmodel.InventoryAreaManualSyncUiState
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class AreaManagementSyncUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun syncNowActionExists() {
        setScreen(InventoryAreaManualSyncUiState.Idle)
        compose.onNodeWithText("Sync now").assertIsDisplayed().assertHasClickAction()
    }

    @Test fun progressDisablesDuplicateAction() {
        setScreen(InventoryAreaManualSyncUiState.Syncing)
        compose.onNodeWithTag("area_sync_now").assertIsNotEnabled()
        compose.onNodeWithTag("area_sync_progress").assertIsDisplayed()
    }

    @Test fun conflictShowsAccessibleComparisonAndExplicitActions() {
        setScreen(CONFLICT)

        compose.onNodeWithText("This area changed on another device").assertIsDisplayed()
        compose.onNodeWithTag("conflict_this_device").assertIsDisplayed()
        compose.onNodeWithTag("conflict_cloud").assertIsDisplayed()
        compose.onNodeWithText("Active").assertIsDisplayed()
        compose.onNodeWithText("Archived").assertIsDisplayed()
        compose.onNodeWithText("Use this device").assertIsEnabled()
        compose.onNodeWithText("Use cloud version").assertIsEnabled()
    }

    @Test fun cancelDismissesConflictWithoutInvokingDecision() {
        var dismisses = 0
        var decisions = 0
        setScreen(CONFLICT, onDismiss = { dismisses++ }, onUseDevice = { decisions++ }, onUseCloud = { decisions++ })

        compose.onNodeWithText("Cancel").performClick()

        assertThat(dismisses).isEqualTo(1)
        assertThat(decisions).isEqualTo(0)
    }

    @Test fun localConstraintMessageRemainsInsideConflict() {
        setScreen(CONFLICT.copy(message = InventoryAreaConflictMessage.LOCAL_CONSTRAINT, conflictingEntityId = "other"))

        compose.onNodeWithTag("conflict_constraint_message").assertIsDisplayed()
        compose.onNodeWithText("Use this device").assertIsDisplayed()
        compose.onNodeWithText("Use cloud version").assertIsDisplayed()
    }

    @Test fun remoteFailureShowsSavedLocallyMessageAndRetry() {
        var retries = 0
        setScreen(InventoryAreaManualSyncUiState.RemoteFailure, onRetry = { retries++ })

        compose.onNodeWithTag("area_sync_message").assertIsDisplayed()
        compose.onNodeWithText("Couldn’t sync right now. Your changes are saved on this device.").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        assertThat(retries).isEqualTo(1)
        compose.onNodeWithText("This area changed on another device").assertDoesNotExist()
    }

    @Test fun previewFailureKeepsLocalCardAndDisablesResolutionChoices() {
        setScreen(InventoryAreaManualSyncUiState.PreviewUnavailable(CONFLICT.conflict, LOCAL, remoteFailure = true))

        compose.onNodeWithTag("conflict_this_device").assertIsDisplayed()
        compose.onNodeWithText("Cloud version couldn’t be loaded.").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsEnabled()
        compose.onNodeWithText("Use this device").assertDoesNotExist()
        compose.onNodeWithText("Use cloud version").assertDoesNotExist()
    }

    @Test fun loadingConflictIsModalAndBlocksEveryMutationEntryPoint() {
        var mutations = 0
        setScreen(
            InventoryAreaManualSyncUiState.LoadingConflict(CONFLICT.conflict),
            onAdd = { mutations++ }, onMoveUp = { mutations++ }, onMoveDown = { mutations++ }
        )

        compose.onNodeWithText("Loading both versions…").assertIsDisplayed()
        compose.onNodeWithTag("conflict_loading_progress").assertIsDisplayed()
        assertMutationControlsDisabled()
        compose.onNodeWithTag("area_sync_now").assertIsNotEnabled()
        compose.onNodeWithText("Use this device").assertDoesNotExist()
        compose.onNodeWithText("Use cloud version").assertDoesNotExist()
        compose.onNodeWithTag("area_add").performClick()
        compose.onNodeWithContentDescription("Move Storage up").performClick()
        compose.onNodeWithContentDescription("Move Prep down").performClick()
        assertThat(mutations).isEqualTo(0)
    }

    @Test fun conflictKeepsMutationsLockedWhileDecisionsRemainAvailable() {
        setScreen(CONFLICT)

        assertMutationControlsDisabled()
        compose.onNodeWithText("Use this device").assertIsEnabled()
        compose.onNodeWithText("Use cloud version").assertIsEnabled()
    }

    @Test fun previewFailureLocksMutationsAndCancelUnlocksThem() {
        var state by mutableStateOf<InventoryAreaManualSyncUiState>(
            InventoryAreaManualSyncUiState.PreviewUnavailable(CONFLICT.conflict, LOCAL, remoteFailure = true)
        )
        setScreen(
            state,
            onDismiss = { state = InventoryAreaManualSyncUiState.Idle },
            syncStateProvider = { state }
        )
        assertMutationControlsDisabled()
        compose.onNodeWithText("Retry").assertIsEnabled()
        compose.onNodeWithText("Use cloud version").assertDoesNotExist()

        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("area_add").assertIsEnabled()
        compose.onNodeWithTag("area_menu_${LOCAL.id.value}").assertIsEnabled()
        compose.onNodeWithContentDescription("Move Prep down").assertIsEnabled()
    }

    @Test fun protectedLocalSnapshotIsTheOneShownWhenPreviewCompletes() {
        var mutations = 0
        var state by mutableStateOf<InventoryAreaManualSyncUiState>(
            InventoryAreaManualSyncUiState.LoadingConflict(CONFLICT.conflict)
        )
        setScreen(
            state,
            onAdd = { mutations++ }, onMoveDown = { mutations++ },
            syncStateProvider = { state }
        )
        compose.onNodeWithTag("area_add").performClick()
        compose.onNodeWithContentDescription("Move Prep down").performClick()
        assertThat(mutations).isEqualTo(0)

        state = CONFLICT
        compose.waitForIdle()
        compose.onNodeWithTag("conflict_this_device").assertIsDisplayed()
        compose.onNode(hasText("Prep") and hasAnyAncestor(hasTestTag("conflict_this_device"))).assertIsDisplayed()
    }

    private fun setScreen(
        syncState: InventoryAreaManualSyncUiState,
        onDismiss: () -> Unit = {},
        onUseDevice: () -> Unit = {},
        onUseCloud: () -> Unit = {},
        onRetry: () -> Unit = {},
        onAdd: (String) -> Unit = {},
        onMoveUp: (Int) -> Unit = {},
        onMoveDown: (Int) -> Unit = {},
        syncStateProvider: (() -> InventoryAreaManualSyncUiState)? = null
    ) {
        compose.setContent {
            AppTheme {
                val currentSyncState = syncStateProvider?.invoke() ?: syncState
                AreaManagementScreen(
                    uiState = AreaManagementUiState(areas = listOf(LOCAL, SECOND), isLoading = false),
                    syncUiState = currentSyncState,
                    areaToArchive = null,
                    areaToEdit = null,
                    newAreaName = "New area",
                    snackbarHostState = SnackbarHostState(),
                    onNewAreaNameChange = {},
                    onSetAreaToArchive = {},
                    onSetAreaToEdit = {},
                    onViewActivity = {},
                    onAddArea = onAdd,
                    onUpdateArea = {},
                    onArchiveArea = {},
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onSyncNow = {},
                    onRetrySync = onRetry,
                    onRetryPreview = onRetry,
                    onUseThisDevice = onUseDevice,
                    onUseCloudVersion = onUseCloud,
                    onDismissConflict = onDismiss,
                    onClearSyncResult = {},
                    onBack = {}
                )
            }
        }
    }

    private fun assertMutationControlsDisabled() {
        compose.onNodeWithTag("area_add").assertIsNotEnabled()
        compose.onNodeWithTag("area_menu_${LOCAL.id.value}").assertIsNotEnabled()
        compose.onNodeWithTag("area_menu_${SECOND.id.value}").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Move Prep down").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Move Storage up").assertIsNotEnabled()
    }

    private companion object {
        val RESTAURANT = RestaurantId("restaurant")
        val AREA = InventoryAreaId("area")
        val LOCAL = InventoryArea(AREA, RESTAURANT, "Prep", "prep", 0, true, Instant.EPOCH, Instant.EPOCH)
        val CLOUD = LOCAL.copy(name = "Kitchen", normalizedName = "kitchen", isActive = false, deletedAt = Instant.EPOCH)
        val SECOND = LOCAL.copy(id = InventoryAreaId("second"), name = "Storage", normalizedName = "storage", sortOrder = 1)
        val CONFLICT = InventoryAreaManualSyncUiState.Conflict(
            InventoryAreaConflictRef(RESTAURANT, AREA.value, "operation"),
            InventoryAreaConflictPreview(LOCAL, CLOUD)
        )
    }
}
