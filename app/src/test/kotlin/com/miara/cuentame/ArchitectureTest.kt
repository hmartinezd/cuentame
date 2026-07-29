package com.miara.cuentame

import com.google.common.truth.Truth.assertWithMessage
import com.miara.cuentame.core.backup.PackageArchitectureRules
import org.junit.Test
import java.io.File

class ArchitectureTest {

    private val rootDir = File(requireNotNull(System.getProperty("cuentame.repoRoot")) {
        "System property 'cuentame.repoRoot' must be set."
    })

    private val sourceDir = File(rootDir, "app/src/main/kotlin/com/miara/cuentame")

    @Test
    fun enforcePackageBoundaries() {
        assertWithMessage("Source directory must exist").that(sourceDir.exists()).isTrue()

        var filesInspected = 0
        sourceDir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            filesInspected++
            val relativePath = file.absolutePath.substringAfter("com/miara/cuentame/")
            val violations = PackageArchitectureRules.violations(relativePath, file.readText())
            
            assertWithMessage("Architecture violations in $relativePath")
                .that(violations).isEmpty()
        }
        assertWithMessage("Zero files inspected").that(filesInspected).isGreaterThan(0)
    }

    @Test
    fun fixtureModelToDatabaseViolationDetected() {
        val source = "import com.miara.cuentame.core.database.entity.User\nclass M"
        val violations = PackageArchitectureRules.violations("core/model/M.kt", source)
        assertWithMessage("Should detect database import in model").that(violations).isNotEmpty()
    }

    @Test
    fun fixtureDomainToComposeViolationDetected() {
        val source = "import androidx.compose.runtime.Composable\nfun D()"
        val violations = PackageArchitectureRules.violations("core/domain/D.kt", source)
        assertWithMessage("Should detect compose import in domain").that(violations).isNotEmpty()
    }

    @Test
    fun fixtureCrossFeatureViolationDetected() {
        val source = "import com.miara.cuentame.feature.other.ui.S\nclass H"
        val violations = PackageArchitectureRules.violations("feature/home/H.kt", source)
        assertWithMessage("Should detect cross-feature import").that(violations).isNotEmpty()
    }
}
