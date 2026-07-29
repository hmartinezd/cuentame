package com.miara.cuentame.core.backup

object PackageArchitectureRules {
    data class ArchitectureViolation(val file: String, val forbiddenImport: String)

    fun violations(relativePath: String, sourceText: String): List<ArchitectureViolation> {
        val lines = sourceText.lines()
        val violations = mutableListOf<ArchitectureViolation>()

        fun check(forbidden: String) {
            if (lines.any { it.startsWith("import $forbidden") }) {
                violations.add(ArchitectureViolation(relativePath, forbidden))
            }
        }

        when {
            relativePath.startsWith("core/model/") -> {
                check("androidx.room")
                check("com.miara.cuentame.core.database")
                check("android.content.Context")
                check("com.miara.cuentame.R")
            }
            relativePath.startsWith("core/domain/") -> {
                check("androidx.compose")
                check("androidx.room")
                check("com.miara.cuentame.core.database")
                check("com.miara.cuentame.R")
                check("android.widget")
                check("android.view")
            }
            relativePath.startsWith("feature/") -> {
                val featureName = relativePath.removePrefix("feature/").substringBefore("/")
                val otherFeatureImport = lines.filter { 
                    it.startsWith("import com.miara.cuentame.feature.") && !it.contains(".feature.$featureName.")
                }
                otherFeatureImport.forEach { violations.add(ArchitectureViolation(relativePath, it)) }
                check("com.miara.cuentame.app.navigation")
            }
            relativePath.startsWith("core/data/") || relativePath.startsWith("core/database/") -> {
                if (lines.any { it.startsWith("import com.miara.cuentame.feature.") }) {
                    violations.add(ArchitectureViolation(relativePath, "com.miara.cuentame.feature"))
                }
            }
            relativePath.startsWith("core/backup/") && !relativePath.contains("/platform/") && !relativePath.contains("/internal/") -> {
                check("com.miara.cuentame.core.database.entity")
            }
        }

        return violations
    }
}
