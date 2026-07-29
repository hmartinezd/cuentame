package com.miara.cuentame

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * Static architecture rule verifier to enforce layer boundaries across the project.
 */
class ArchitectureTest {

    private val rootDir = File(".").canonicalFile

    @Test
    fun `core model files do not import Room or Context`() {
        val modelDirs = listOf(
            File(rootDir, "core/model/src/main/kotlin"),
            File(rootDir, "app/src/main/kotlin/com/miara/cuentame/core/model")
        )

        modelDirs.filter { it.exists() }.forEach { dir ->
            dir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
                val lines = file.readLines()
                val forbiddenRoom = lines.filter { it.startsWith("import androidx.room.") }
                val forbiddenContext = lines.filter { it.startsWith("import android.content.Context") }

                assertWithMessage("Forbidden Room imports in ${file.name}")
                    .that(forbiddenRoom)
                    .isEmpty()
                assertWithMessage("Forbidden Context imports in ${file.name}")
                    .that(forbiddenContext)
                    .isEmpty()
            }
        }
    }

    @Test
    fun `core domain files do not import Android UI or Room DAOs`() {
        val domainDirs = listOf(
            File(rootDir, "core/domain/src/main/kotlin"),
            File(rootDir, "app/src/main/kotlin/com/miara/cuentame/core/domain")
        )

        domainDirs.filter { it.exists() }.forEach { dir ->
            dir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
                val lines = file.readLines()
                val forbiddenCompose = lines.filter { it.startsWith("import androidx.compose.") }
                val forbiddenRoomDao = lines.filter { it.startsWith("import com.miara.cuentame.core.database.dao.") }

                assertWithMessage("Forbidden Compose imports in ${file.name}")
                    .that(forbiddenCompose)
                    .isEmpty()
                assertWithMessage("Forbidden Room DAO imports in ${file.name}")
                    .that(forbiddenRoomDao)
                    .isEmpty()
            }
        }
    }
}
