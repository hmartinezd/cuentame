package com.miara.cuentame

import com.google.common.truth.Truth.assertWithMessage
import com.miara.cuentame.core.backup.PackageArchitectureRules
import org.junit.Test
import java.io.File

class ArchitectureTest {

    private val repoRoot = System.getProperty("cuentame.repoRoot")

    @Test
    fun enforcePackageBoundaries() {
        assertWithMessage("System property 'cuentame.repoRoot' must be set. Check build.gradle.kts.")
            .that(repoRoot).isNotNull()
        
        val rootDir = File(repoRoot!!)
        val sourceDir = File(rootDir, "app/src/main/kotlin/com/miara/cuentame")
        assertWithMessage("Source directory must exist at ${sourceDir.absolutePath}")
            .that(sourceDir.exists()).isTrue()

        var filesInspected = 0
        sourceDir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            filesInspected++
            val relativePath = file.absolutePath.substringAfter("com/miara/cuentame/")
            val violations = PackageArchitectureRules.violations(relativePath, file.readText())
            
            assertWithMessage("Architecture violations in $relativePath:\n${violations.joinToString("\n") { it.forbiddenImport }}")
                .that(violations).isEmpty()
        }
        assertWithMessage("Zero files inspected").that(filesInspected).isGreaterThan(0)
    }

    @Test
    fun ruleModelToDatabaseViolationDetected() {
        val source = "import com.miara.cuentame.core.database.entity.UserEntity\nclass M"
        val violations = PackageArchitectureRules.violations("core/model/M.kt", source)
        assertWithMessage("Should detect database import in model").that(violations).isNotEmpty()
    }

    @Test
    fun rulePureBackupToDatabaseViolationDetected() {
        val source = "import com.miara.cuentame.core.database.entity.PurchaseReceiptEntity\nclass V"
        val violations = PackageArchitectureRules.violations("core/backup/api/V.kt", source)
        assertWithMessage("Pure backup API should not depend on database entities").that(violations).isNotEmpty()
    }

    @Test
    fun ruleDomainToComposeViolationDetected() {
        val source = "import androidx.compose.runtime.Composable\nfun D()"
        val violations = PackageArchitectureRules.violations("core/domain/D.kt", source)
        assertWithMessage("Should detect compose import in domain").that(violations).isNotEmpty()
    }

    @Test
    fun aliasedForbiddenImportDetected() {
        val source = "import com.miara.cuentame.core.database.entity.UserEntity as U\nclass M"
        val violations = PackageArchitectureRules.violations("core/model/M.kt", source)
        assertWithMessage("Should detect aliased forbidden import").that(violations).isNotEmpty()
    }
}
