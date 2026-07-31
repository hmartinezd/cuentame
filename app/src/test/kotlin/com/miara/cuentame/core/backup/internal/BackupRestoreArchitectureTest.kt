package com.miara.cuentame.core.backup.internal

import com.miara.cuentame.core.backup.platform.BackupRestoreCoordinatorImpl
import org.junit.Test

class BackupRestoreArchitectureTest {

    @Test
    fun `active production coordinator has no dependency on forbidden attachment components`() {
        val coordinatorClass = BackupRestoreCoordinatorImpl::class.java
        val constructors = coordinatorClass.constructors
        
        val forbiddenTypes = setOf(
            "com.miara.cuentame.core.backup.internal.RestoreAttachmentInstaller",
            "com.miara.cuentame.core.backup.internal.BackupArchiveRestoreStager"
        )
        
        constructors.forEach { constructor ->
            constructor.parameterTypes.forEach { paramType ->
                val typeName = paramType.name
                if (forbiddenTypes.contains(typeName)) {
                    throw AssertionError("Coordinator depends on forbidden attachment component: $typeName")
                }
            }
        }
    }
}
