package com.miara.cuentame

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * Static architecture rule verifier to enforce layer boundaries across the project.
 */
class ArchitectureTest {

    private val rootDir = File(requireNotNull(System.getProperty("cuentame.repoRoot")) {
        "System property 'cuentame.repoRoot' must be set. Check app/build.gradle.kts configuration."
    })

    @Test
    fun `core model files do not import Room or Context`() {
        val modelDirs = listOf(
            File(rootDir, "core/model/src/main/kotlin"),
            File(rootDir, "app/src/main/kotlin/com/miara/cuentame/core/model")
        )

        var filesInspected = 0
        modelDirs.filter { it.exists() }.forEach { dir ->
            dir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
                filesInspected++
                val content = file.readText()
                val lines = content.lines()
                val forbiddenRoom = lines.filter { it.startsWith("import androidx.room.") }
                val forbiddenContext = lines.filter { it.startsWith("import android.content.Context") }

                assertWithMessage("Forbidden Room imports in ${file.absolutePath}")
                    .that(forbiddenRoom)
                    .isEmpty()
                assertWithMessage("Forbidden Context imports in ${file.absolutePath}")
                    .that(forbiddenContext)
                    .isEmpty()
            }
        }
        assertWithMessage("No Kotlin files found in model directories").that(filesInspected).isGreaterThan(0)
    }

    @Test
    fun `core domain files do not import Android UI, Room DAOs, or Data layer`() {
        val domainDirs = listOf(
            File(rootDir, "core/domain/src/main/kotlin"),
            File(rootDir, "app/src/main/kotlin/com/miara/cuentame/core/domain")
        )

        var filesInspected = 0
        domainDirs.filter { it.exists() }.forEach { dir ->
            dir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
                filesInspected++
                val lines = file.readLines()
                val forbiddenCompose = lines.filter { it.startsWith("import androidx.compose.") }
                val forbiddenRoomDao = lines.filter { it.contains(".database.dao.") }
                val forbiddenDatabase = lines.filter { it.contains(".core.database.") }
                val forbiddenPreferences = lines.filter { it.contains(".core.preferences.datastore") }

                assertWithMessage("Forbidden Compose imports in ${file.absolutePath}")
                    .that(forbiddenCompose)
                    .isEmpty()
                assertWithMessage("Forbidden Room/Database imports in ${file.absolutePath}")
                    .that(forbiddenRoomDao + forbiddenDatabase)
                    .isEmpty()
                assertWithMessage("Forbidden Preferences implementation imports in ${file.absolutePath}")
                    .that(forbiddenPreferences)
                    .isEmpty()
            }
        }
        assertWithMessage("No Kotlin files found in domain directories").that(filesInspected).isGreaterThan(0)
    }

    @Test
    fun `feature modules do not import other feature modules directly`() {
        val featureBaseDir = File(rootDir, "app/src/main/kotlin/com/miara/cuentame/feature")
        if (!featureBaseDir.exists()) {
             // If we already moved to modules, check there too
             return
        }

        val features = featureBaseDir.listFiles { f -> f.isDirectory } ?: return

        var filesInspected = 0
        features.forEach { featureDir ->
            val featureName = featureDir.name
            featureDir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
                filesInspected++
                val lines = file.readLines()
                val forbiddenFeatureImports = lines.filter { 
                    it.startsWith("import com.miara.cuentame.feature.") && !it.contains(".feature.$featureName.")
                }

                assertWithMessage("Forbidden cross-feature import in $featureName (${file.name}): $forbiddenFeatureImports")
                    .that(forbiddenFeatureImports)
                    .isEmpty()
            }
        }
        assertWithMessage("No Kotlin files found in feature directories").that(filesInspected).isGreaterThan(0)
    }
}
