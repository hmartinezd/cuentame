package com.miara.cuentame

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

class ArchitectureTest {

    private val rootDir = File(requireNotNull(System.getProperty("cuentame.repoRoot")) {
        "System property 'cuentame.repoRoot' must be set."
    })

    private val sourceDir = File(rootDir, "app/src/main/kotlin/com/miara/cuentame")

    @Test
    fun `core model does not import Room, database implementations, or Android Context`() {
        val modelDir = File(sourceDir, "core/model")
        assertWithMessage("Model directory must exist").that(modelDir.exists()).isTrue()

        var filesInspected = 0
        modelDir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            filesInspected++
            val lines = file.readLines()
            
            assertNoImport(file, lines, "androidx.room")
            assertNoImport(file, lines, "com.miara.cuentame.core.database")
            assertNoImport(file, lines, "android.content.Context")
            assertNoImport(file, lines, "com.miara.cuentame.R")
        }
        assertWithMessage("Zero model files inspected").that(filesInspected).isGreaterThan(0)
    }

    @Test
    fun `core domain does not import UI, Room, or database implementations`() {
        val domainDir = File(sourceDir, "core/domain")
        assertWithMessage("Domain directory must exist").that(domainDir.exists()).isTrue()

        var filesInspected = 0
        domainDir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            filesInspected++
            val lines = file.readLines()

            assertNoImport(file, lines, "androidx.compose")
            assertNoImport(file, lines, "androidx.room")
            assertNoImport(file, lines, "com.miara.cuentame.core.database")
            assertNoImport(file, lines, "com.miara.cuentame.R")
        }
        assertWithMessage("Zero domain files inspected").that(filesInspected).isGreaterThan(0)
    }

    @Test
    fun `feature packages are isolated from each other`() {
        val featureDir = File(sourceDir, "feature")
        assertWithMessage("Feature directory must exist").that(featureDir.exists()).isTrue()

        val features = featureDir.listFiles { f -> f.isDirectory } ?: emptyArray()
        assertWithMessage("No features found").that(features).isNotEmpty()

        var totalFilesInspected = 0
        features.forEach { feature ->
            val featureName = feature.name
            feature.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
                totalFilesInspected++
                val lines = file.readLines()
                
                // Features should not import other features directly
                // (Except through shared core or if specifically allowed, but we want strict isolation now)
                val otherFeatureImports = lines.filter { 
                    it.startsWith("import com.miara.cuentame.feature.") && !it.contains(".feature.$featureName.")
                }
                assertWithMessage("Forbidden cross-feature import in $featureName (${file.name})")
                    .that(otherFeatureImports).isEmpty()
            }
        }
        assertWithMessage("Zero feature files inspected").that(totalFilesInspected).isGreaterThan(0)
    }

    @Test
    fun `feature packages do not import app implementation packages`() {
        val featureDir = File(sourceDir, "feature")
        featureDir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val lines = file.readLines()
            assertNoImport(file, lines, "com.miara.cuentame.app.navigation")
            // Add other app internal packages here if needed
        }
    }

    @Test
    fun `pure backup logic does not import Room entities`() {
        val backupDir = File(sourceDir, "core/backup")
        backupDir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            // Platform adapters are allowed to use Room/Android, but pure models/validators are not
            if (!file.absolutePath.contains("/platform/") && !file.absolutePath.contains("/internal/")) {
                 val lines = file.readLines()
                 assertNoImport(file, lines, "com.miara.cuentame.core.database.entity")
            }
        }
    }

    private fun assertNoImport(file: File, lines: List<String>, forbidden: String) {
        val violations = lines.filter { it.startsWith("import $forbidden") }
        assertWithMessage("Forbidden import '$forbidden' in ${file.absolutePath}")
            .that(violations).isEmpty()
    }
}
