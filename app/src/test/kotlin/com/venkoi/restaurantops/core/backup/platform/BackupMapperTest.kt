package com.venkoi.restaurantops.core.backup.platform

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.backup.model.PurchaseLineBackupDto
import com.venkoi.restaurantops.core.database.entity.PurchaseLineEntity
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
        
        // Ingredient with exact user-configured par and reorder point.
        val ingDto = com.venkoi.restaurantops.core.backup.model.IngredientBackupDto(
            id = "i1",
            restaurantId = "r1",
            name = "Name",
            normalizedName = "name",
            categoryId = "c1",
            baseUnitId = "u1",
            defaultAreaId = "a1",
            sku = "sku",
            notes = "notes",
            reorderPointBase = "8.125",
            isActive = true,
            createdAt = 0L,
            updatedAt = 0L,
            deletedAt = null,
            parLevelBase = "23.75"
        )
        val ingEntity = BackupMapper.run { ingDto.toEntity() }
        assertThat(ingEntity.parLevelBase?.toPlainString()).isEqualTo("23.75")
        assertThat(ingEntity.reorderPointBase?.toPlainString()).isEqualTo("8.125")
        assertThat(BackupMapper.run { ingEntity.toDto() }).isEqualTo(ingDto)

        // Unit with factorToCanonical
        val unitDto = com.venkoi.restaurantops.core.backup.model.UnitBackupDto(
            "u1", "Unit", "u", "MASS", "2.5", true, 0
        )
        val unitEntity = BackupMapper.run { unitDto.toEntity() }
        assertThat(unitEntity.factorToCanonical.toPlainString()).isEqualTo("2.5")
        assertThat(BackupMapper.run { unitEntity.toDto() }).isEqualTo(unitDto)
    }

    @Test
    fun `Ingredient mapping preserves null par and reorder point`() {
        val dto = com.venkoi.restaurantops.core.backup.model.IngredientBackupDto(
            id = "i-null", restaurantId = "r1", name = "Salt", normalizedName = "salt",
            categoryId = null, baseUnitId = "u1", defaultAreaId = null, sku = null, notes = null,
            reorderPointBase = null, isActive = true, createdAt = 0L, updatedAt = 0L,
            deletedAt = null, parLevelBase = null
        )

        val restored = BackupMapper.run { dto.toEntity() }

        assertThat(restored.parLevelBase).isNull()
        assertThat(restored.reorderPointBase).isNull()
        assertThat(BackupMapper.run { restored.toDto() }).isEqualTo(dto)
    }

    @Test
    fun `PreparationRecipe mapping round-trip`() {
        val dto = com.venkoi.restaurantops.core.backup.model.PreparationRecipeBackupDto(
            id = "rec-1",
            restaurantId = "rest-1",
            outputIngredientId = "ing-1",
            name = "Recipe",
            normalizedName = "recipe",
            standardYieldQuantity = "5",
            standardYieldQuantityBase = "5",
            yieldUnitOptionId = "unit-1",
            status = "ACTIVE",
            notes = "notes",
            createdAt = 1000L,
            updatedAt = 2000L,
            archivedAt = null
        )

        val entity = BackupMapper.run { dto.toEntity() }
        assertThat(entity.standardYieldQuantity?.toPlainString()).isEqualTo("5")
        assertThat(entity.status).isEqualTo("ACTIVE")

        val roundTrip = BackupMapper.run { entity.toDto() }
        assertThat(roundTrip).isEqualTo(dto)
    }

    @Test
    fun `PreparationRecipeComponent mapping round-trip`() {
        val dto = com.venkoi.restaurantops.core.backup.model.PreparationRecipeComponentBackupDto(
            id = "comp-1",
            recipeId = "rec-1",
            componentIngredientId = "ing-2",
            unitOptionId = "unit-2",
            quantityEntered = "10.5",
            quantityBase = "10.5",
            sortOrder = 5,
            notes = "comp notes",
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val entity = BackupMapper.run { dto.toEntity() }
        assertThat(entity.quantityEntered.toPlainString()).isEqualTo("10.5")
        assertThat(entity.sortOrder).isEqualTo(5)

        val roundTrip = BackupMapper.run { entity.toDto() }
        assertThat(roundTrip).isEqualTo(dto)
    }
}
