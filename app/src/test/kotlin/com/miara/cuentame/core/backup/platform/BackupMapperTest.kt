package com.miara.cuentame.core.backup.platform

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.backup.model.PurchaseLineBackupDto
import com.miara.cuentame.core.database.entity.PurchaseLineEntity
import org.junit.Test

class BackupMapperTest {

    @Test
    fun `PurchaseLine mapping preserves lineTotal and unitCostBase when they differ`() {
        // Arrange
        val entity = PurchaseLineEntity(
            id = "l1",
            purchaseReceiptId = "r1",
            ingredientId = "i1",
            areaId = "a1",
            ingredientUnitOptionId = "o1",
            quantityEntered = "20",
            quantityBase = "20",
            lineTotal = "60", // Total paid
            unitCostBase = "3", // Cost per unit
            notes = "test notes",
            createdAt = 1000L,
            updatedAt = 2000L
        )

        // Act & Assert: Entity -> DTO
        val dto = BackupMapper.run { entity.toDto() }
        assertThat(dto.lineTotal).isEqualTo("60")
        assertThat(dto.unitCostBase).isEqualTo("3")

        // Act & Assert: DTO -> Entity
        val entityFromDto = BackupMapper.run { dto.toEntity() }
        assertThat(entityFromDto.lineTotal).isEqualTo("60")
        assertThat(entityFromDto.unitCostBase).isEqualTo("3")

        // Round-trip: Entity -> DTO -> Entity
        val entityRoundTrip = BackupMapper.run { dto.toEntity() }
        assertThat(entityRoundTrip).isEqualTo(entity)
        
        // Round-trip: DTO -> Entity -> DTO
        val dtoRoundTrip = BackupMapper.run { entityFromDto.toDto() }
        assertThat(dtoRoundTrip).isEqualTo(dto)
    }

    @Test
    fun `PurchaseLine mapping handles positional swapping regression`() {
        // This test specifically uses values that would be swapped if positional mapping is wrong.
        // PurchaseLineEntity: ... quantityBase, lineTotal, unitCostBase, notes ...
        // PurchaseLineBackupDto: ... quantityBase, unitCostBase, lineTotal, notes ...
        
        val dto = PurchaseLineBackupDto(
            id = "l1",
            purchaseReceiptId = "r1",
            ingredientId = "i1",
            areaId = "a1",
            ingredientUnitOptionId = "o1",
            quantityEntered = "10",
            quantityBase = "10",
            unitCostBase = "4.5", // Index 7 in DTO
            lineTotal = "45",    // Index 8 in DTO
            notes = null,
            createdAt = 0L,
            updatedAt = 0L
        )

        val entity = BackupMapper.run { dto.toEntity() }
        
        // If swapped, lineTotal would be 4.5 and unitCostBase would be 45.
        assertThat(entity.lineTotal).isEqualTo("45")
        assertThat(entity.unitCostBase).isEqualTo("4.5")
    }

    @Test
    fun `Audit all mappers for round-trip consistency`() {
        // We verify that multiple audited types preserve fields correctly through round trips.
        
        // Ingredient with reorderPointBase
        val ingDto = com.miara.cuentame.core.backup.model.IngredientBackupDto(
            "i1", "r1", "Name", "name", "c1", "u1", "a1", "sku", "notes", "10.5", true, 0L, 0L, null
        )
        val ingEntity = BackupMapper.run { ingDto.toEntity() }
        assertThat(ingEntity.reorderPointBase?.toPlainString()).isEqualTo("10.5")
        assertThat(BackupMapper.run { ingEntity.toDto() }).isEqualTo(ingDto)

        // Unit with factorToCanonical
        val unitDto = com.miara.cuentame.core.backup.model.UnitBackupDto(
            "u1", "Unit", "u", "MASS", "2.5", true, 0
        )
        val unitEntity = BackupMapper.run { unitDto.toEntity() }
        assertThat(unitEntity.factorToCanonical.toPlainString()).isEqualTo("2.5")
        assertThat(BackupMapper.run { unitEntity.toDto() }).isEqualTo(unitDto)
    }
}
