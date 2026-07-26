package com.miara.cuentame.feature.waste.ui

import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ActivityScenario
import com.miara.cuentame.MainActivity

fun ComposeTestRule.launchMainActivity(): ActivityScenario<MainActivity> {
    val scenario = ActivityScenario.launch(MainActivity::class.java)
    waitForIdle()
    return scenario
}

fun ComposeTestRule.waitForTag(
    tag: String,
    timeoutMillis: Long = 15_000
) {
    try {
        waitUntil(timeoutMillis) {
            onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        waitForIdle()
    } catch (error: Throwable) {
        val tree = try {
            onAllNodes(isRoot(), useUnmergedTree = true).onFirst().printToString()
        } catch (e: Exception) {
            "Could not print tree: ${e.message}"
        }
        Log.e("WasteTestHelper", "TIMEOUT waiting for tag: $tag")
        Log.e("WasteTestHelper", "Tree: $tree")
        println("TIMEOUT waiting for tag: $tag")
        println(tree)
        throw error
    }
}

fun ComposeTestRule.waitForHomeReady() {
    waitForIdle()
    waitForTag("home_date_range_selector")
}

fun ComposeTestRule.openWasteHistory() {
    onNodeWithTag("view_waste_button").performScrollTo().performClick()
    waitForIdle()
    waitForTag("waste_list_screen")
}

fun ComposeTestRule.openWasteEvent(eventId: String) {
    waitForTag("waste_item_$eventId")
    onNodeWithTag("waste_item_$eventId").performScrollTo().performClick()
    waitForIdle()
    // Wait for the detail content specifically to ensure navigation is complete
    waitForWasteDetail()
}

fun ComposeTestRule.waitForWasteDetail() {
    waitForTag("waste_detail_screen")
    waitForTag("waste_detail_content")
}

fun ComposeTestRule.waitForWasteStatus(
    expectedText: String,
    timeoutMillis: Long = 20_000
) {
    try {
        waitUntil(timeoutMillis) {
            onAllNodes(
                hasTestTag("waste_status_chip") and
                    hasText(expectedText, substring = true, ignoreCase = true)
            ).fetchSemanticsNodes().isNotEmpty()
        }
    } catch (error: Throwable) {
        val tree = try {
            onAllNodes(isRoot(), useUnmergedTree = true).onFirst().printToString()
        } catch (e: Exception) {
            "Could not print tree: ${e.message}"
        }
        println("TIMEOUT waiting for waste status: $expectedText")
        println(tree)
        throw error
    }
    waitForIdle()
}

fun ComposeTestRule.openWasteEdit() {
    waitForTag("waste_edit_button")
    onNodeWithTag("waste_edit_button").performClick()
    waitForIdle()
    waitForTag("ingredient_selector")
}

fun ComposeTestRule.dismissOpenPopup(itemTag: String) {
    onNodeWithTag(itemTag).performClick()
    waitForIdle()
    waitUntil(5_000) {
        onAllNodesWithTag(itemTag).fetchSemanticsNodes().isEmpty()
    }
}
