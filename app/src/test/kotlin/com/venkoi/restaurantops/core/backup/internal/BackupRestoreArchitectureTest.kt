package com.venkoi.restaurantops.core.backup.internal

import com.venkoi.restaurantops.core.backup.platform.BackupRestoreCoordinatorImpl
import org.junit.Test

class BackupRestoreArchitectureTest {

    @Test
    fun `active production coordinator has required attachment components`() {
        val coordinatorClass = BackupRestoreCoordinatorImpl::class.java
        val constructors = coordinatorClass.constructors
        
        val requiredTypes = setOf(
            "com.venkoi.restaurantops.core.backup.internal.RestoreAttachmentInstaller",
            "com.venkoi.restaurantops.core.backup.internal.BackupArchiveRestoreStager"
        )
        
        val foundTypes = mutableSetOf<String>()
        constructors.forEach { constructor ->
            constructor.parameterTypes.forEach { paramType ->
                if (requiredTypes.contains(paramType.name)) {
                    foundTypes.add(paramType.name)
                }
            }
        }
        
        if (foundTypes != requiredTypes) {
            throw AssertionError("Coordinator is missing required attachment components. Found: $foundTypes, Expected: $requiredTypes")
        }
    }
}
