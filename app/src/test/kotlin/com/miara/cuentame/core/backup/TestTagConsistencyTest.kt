package com.miara.cuentame.core.backup

import org.junit.Test
import java.io.File
import com.google.common.truth.Truth.assertWithMessage

class TestTagConsistencyTest {

    private val criticalTags = setOf(
        "home_screen",
        "view_reports_button",
        "reports_screen",
        "reports_back_button",
        "dashboard_restaurant_name",
        "dashboard_inventory_value",
        "dashboard_purchase_spend",
        "dashboard_waste_value"
    )

    private val obsoleteTags = setOf(
        "home_reports_button"
    )

    @Test
    fun `ensure no obsolete tags are used in android tests`() {
        val testDir = File("src/androidTest/kotlin")
        val files = testDir.walkTopDown().filter { it.extension == "kt" }.toList()
        
        for (file in files) {
            val content = file.readText()
            for (obsolete in obsoleteTags) {
                assertWithMessage("File ${file.path} contains obsolete tag '$obsolete'")
                    .that(content).doesNotContain("\"$obsolete\"")
            }
        }
    }

    @Test
    fun `ensure critical tags exist in production source`() {
        val srcDir = File("src/main/kotlin")
        val content = srcDir.walkTopDown().filter { it.extension == "kt" }.map { it.readText() }.joinToString("\n")
        
        for (tag in criticalTags) {
            assertWithMessage("Critical tag '$tag' not found in production source")
                .that(content).contains("\"$tag\"")
        }
    }
}
