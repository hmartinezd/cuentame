package com.venkoi.cuentame.core.backup

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
        "dashboard_waste_value",
        "dashboard_negative_balance_count",
        "dashboard_top_waste_empty",
        "dashboard_recent_activity_empty",
        "purchase_error_snackbar_content",
        "waste_error_snackbar_content"
    )

    private val obsoleteTags = setOf(
        "home_reports_button"
    )

    @Test
    fun `ensure no obsolete tags are used in android tests`() {
        val testDir = File("src/androidTest/kotlin")
        // Use a failable check if directory missing
        assertWithMessage("Test directory not found").that(testDir.exists()).isTrue()
        
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
        assertWithMessage("Source directory not found").that(srcDir.exists()).isTrue()
        
        val content = srcDir.walkTopDown().filter { it.extension == "kt" }.map { it.readText() }.joinToString("\n")
        
        for (tag in criticalTags) {
            assertWithMessage("Critical tag '$tag' not found in production source")
                .that(content).contains("\"$tag\"")
        }
    }

    @Test
    fun `HomeUiTest must not reference reports_view_inventory_details without navigation`() {
        val file = File("src/androidTest/kotlin/com/venkoi/cuentame/feature/home/HomeUiTest.kt")
        assertWithMessage("HomeUiTest source file not found").that(file.exists()).isTrue()
        
        val content = file.readText()
        val tag = "reports_view_inventory_details"
        
        // Find all test methods
        val testMethods = content.split("@Test").drop(1)
        for (method in testMethods) {
            if (method.contains("\"$tag\"")) {
                assertWithMessage("HomeUiTest method contains '$tag' but does not appear to navigate to reports screen")
                    .that(method).contains("\"reports_screen\"")
            }
        }
    }
}
