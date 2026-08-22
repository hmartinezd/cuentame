package com.venkoi.restaurantops.feature.areas.ui

import androidx.compose.material3.SnackbarHostState
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

    private fun setScreen(
        syncState: InventoryAreaManualSyncUiState,
        onDismiss: () -> Unit = {},
        onUseDevice: () -> Unit = {},
        onUseCloud: () -> Unit = {},
        onRetry: () -> Unit = {}
    ) {
        compose.setContent {
            AppTheme {
                AreaManagementScreen(
                    uiState = AreaManagementUiState(areas = emptyList(), isLoading = false),
                    syncUiState = syncState,
                    areaToArchive = null,
                    areaToEdit = null,
                    newAreaName = "",
                    snackbarHostState = SnackbarHostState(),
                    onNewAreaNameChange = {},
                    onSetAreaToArchive = {},
                    onSetAreaToEdit = {},
                    onViewActivity = {},
                    onAddArea = {},
                    onUpdateArea = {},
                    onArchiveArea = {},
                    onMoveUp = {},
                    onMoveDown = {},
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

    private companion object {
        val RESTAURANT = RestaurantId("restaurant")
        val AREA = InventoryAreaId("area")
        val LOCAL = InventoryArea(AREA, RESTAURANT, "Prep", "prep", 0, true, Instant.EPOCH, Instant.EPOCH)
        val CLOUD = LOCAL.copy(name = "Kitchen", normalizedName = "kitchen", isActive = false, deletedAt = Instant.EPOCH)
        val CONFLICT = InventoryAreaManualSyncUiState.Conflict(
            InventoryAreaConflictRef(RESTAURANT, AREA.value, "operation"),
            InventoryAreaConflictPreview(LOCAL, CLOUD)
        )
    }
}
