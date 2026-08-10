package com.miara.cuentame.feature.ingredient.import.domain

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.domain.repository.IngredientCategoryRepository
import com.miara.cuentame.core.domain.repository.IngredientRepository
import com.miara.cuentame.core.domain.repository.InventoryAreaRepository
import com.miara.cuentame.core.domain.repository.SupplierRepository
import com.miara.cuentame.core.domain.repository.UnitRepository
import com.miara.cuentame.core.domain.repository.SupplierItemMappingRepository
import com.miara.cuentame.core.model.inventory.UnitDimension
import com.miara.cuentame.core.model.inventory.UnitOfMeasure
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class CsvImportServiceTest {
    private val ingredientRepository = mockk<IngredientRepository>()
    private val categoryRepository = mockk<IngredientCategoryRepository>()
    private val areaRepository = mockk<InventoryAreaRepository>()
    private val supplierRepository = mockk<SupplierRepository>()
    private val unitRepository = mockk<UnitRepository>()
    private val mappingRepository = mockk<SupplierItemMappingRepository>()
    
    private lateinit var service: CsvImportService
    private val restaurantId = RestaurantId("rest-1")

    @Before
    fun setup() {
        service = CsvImportService(
            ingredientRepository,
            categoryRepository,
            areaRepository,
            supplierRepository,
            unitRepository,
            mappingRepository
        )
        
        coEvery { ingredientRepository.getIngredients(any(), any()) } returns emptyList()
        every { categoryRepository.observeActiveCategories() } returns flowOf(emptyList())
        every { categoryRepository.observeAllCategories() } returns flowOf(emptyList())
        every { areaRepository.observeActiveAreas() } returns flowOf(emptyList())
        every { supplierRepository.observeSuppliers(any(), any()) } returns flowOf(emptyList())
        coEvery { mappingRepository.getAllMappings(any()) } returns emptyList()
        
        val lb = UnitOfMeasure(com.miara.cuentame.core.common.ids.UnitId("lb"), "lb", "Pound", UnitDimension.MASS, BigDecimal.ONE, true, 0)
        every { unitRepository.observeAll() } returns flowOf(listOf(lb))
    }

    @Test
    fun `process valid row`() = runTest {
        val rawRows = listOf(
            mapOf(
                CsvParser.HEADER_INGREDIENT_NAME to "Tomato",
                CsvParser.HEADER_BASE_UNIT to "lb"
            )
        )
        
        val result = service.processCsv(restaurantId, rawRows)
        
        assertThat(result.rows).hasSize(1)
        assertThat(result.rows[0].status).isEqualTo(CsvImportRowStatus.READY)
        assertThat(result.rows[0].normalizedData?.name).isEqualTo("Tomato")
    }

    @Test
    fun `detect unknown unit error`() = runTest {
        val rawRows = listOf(
            mapOf(
                CsvParser.HEADER_INGREDIENT_NAME to "Tomato",
                CsvParser.HEADER_BASE_UNIT to "unknown"
            )
        )
        
        val result = service.processCsv(restaurantId, rawRows)
        
        assertThat(result.rows[0].status).isEqualTo(CsvImportRowStatus.ERROR)
        assertThat(result.rows[0].issues.any { it.message.contains("Unknown unit") }).isTrue()
    }

    @Test
    fun `detect duplicate name in CSV error`() = runTest {
        val rawRows = listOf(
            mapOf(
                CsvParser.HEADER_INGREDIENT_NAME to "Tomato",
                CsvParser.HEADER_BASE_UNIT to "lb"
            ),
            mapOf(
                CsvParser.HEADER_INGREDIENT_NAME to "Tomato",
                CsvParser.HEADER_BASE_UNIT to "lb"
            )
        )
        
        val result = service.processCsv(restaurantId, rawRows)
        
        assertThat(result.rows[1].status).isEqualTo(CsvImportRowStatus.ERROR)
        assertThat(result.rows[1].issues.any { it.message.contains("Duplicate ingredient name") }).isTrue()
    }

    @Test
    fun `detect archived category conflict error`() = runTest {
        val archivedCategory = com.miara.cuentame.core.model.ingredient.IngredientCategory(
            id = com.miara.cuentame.core.common.ids.IngredientCategoryId("cat-1"),
            restaurantId = restaurantId,
            name = "Archived Cat",
            normalizedName = "archived cat",
            sortOrder = 0,
            isActive = true,
            createdAt = java.time.Instant.EPOCH,
            updatedAt = java.time.Instant.EPOCH,
            deletedAt = java.time.Instant.now()
        )
        every { categoryRepository.observeAllCategories() } returns flowOf(listOf(archivedCategory))

        val rawRows = listOf(
            mapOf(
                CsvParser.HEADER_INGREDIENT_NAME to "Tomato",
                CsvParser.HEADER_BASE_UNIT to "lb",
                CsvParser.HEADER_CATEGORY to "Archived Cat"
            )
        )
        
        val result = service.processCsv(restaurantId, rawRows)
        
        assertThat(result.rows[0].status).isEqualTo(CsvImportRowStatus.ERROR)
        assertThat(result.rows[0].issues.any { it.message.contains("Category is archived") }).isTrue()
    }
}
