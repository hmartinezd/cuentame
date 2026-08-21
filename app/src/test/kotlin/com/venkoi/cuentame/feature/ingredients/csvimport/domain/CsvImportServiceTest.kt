package com.venkoi.cuentame.feature.ingredients.csvimport.domain

import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.domain.repository.IngredientCategoryRepository
import com.venkoi.cuentame.core.domain.repository.IngredientRepository
import com.venkoi.cuentame.core.domain.repository.InventoryAreaRepository
import com.venkoi.cuentame.core.domain.repository.SupplierRepository
import com.venkoi.cuentame.core.domain.repository.UnitRepository
import com.venkoi.cuentame.core.domain.repository.SupplierItemMappingRepository
import com.venkoi.cuentame.core.model.inventory.UnitDimension
import com.venkoi.cuentame.core.model.inventory.UnitOfMeasure
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

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
        coEvery { categoryRepository.getAllCategoriesForRestaurant(any()) } returns emptyList()
        every { areaRepository.observeActiveAreas() } returns flowOf(emptyList())
        every { supplierRepository.observeSuppliers(any(), any()) } returns flowOf(emptyList())
        coEvery { mappingRepository.getAllMappings(any()) } returns emptyList()
        
        val lb = UnitOfMeasure(com.venkoi.cuentame.core.common.ids.UnitId("mass_lb"), "Pound", "lb", UnitDimension.MASS, BigDecimal.ONE, true, 0)
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
                CsvParser.HEADER_BASE_UNIT to "crate"
            )
        )
        
        val result = service.processCsv(restaurantId, rawRows)
        
        assertThat(result.rows[0].status).isEqualTo(CsvImportRowStatus.ERROR)
        assertThat(result.rows[0].issues.map { it.code }).contains(CsvImportIssueCode.UNKNOWN_UNIT)
    }

    @Test
    fun `stable system unit ids resolve supported import aliases`() = runTest {
        val units = listOf(
            UnitOfMeasure(com.venkoi.cuentame.core.common.ids.UnitId("mass_lb"), "Pound", "lb", UnitDimension.MASS, BigDecimal.ONE, true, 0),
            UnitOfMeasure(com.venkoi.cuentame.core.common.ids.UnitId("mass_oz"), "Ounce", "oz", UnitDimension.MASS, BigDecimal.ONE, true, 1),
            UnitOfMeasure(com.venkoi.cuentame.core.common.ids.UnitId("mass_kg"), "Kilogram", "kg", UnitDimension.MASS, BigDecimal.ONE, true, 2),
            UnitOfMeasure(com.venkoi.cuentame.core.common.ids.UnitId("volume_ml"), "Milliliter", "ml", UnitDimension.VOLUME, BigDecimal.ONE, true, 3),
            UnitOfMeasure(com.venkoi.cuentame.core.common.ids.UnitId("volume_gallon_us"), "US Gallon", "gal", UnitDimension.VOLUME, BigDecimal.ONE, true, 4),
            UnitOfMeasure(com.venkoi.cuentame.core.common.ids.UnitId("count_each"), "Each", "ea", UnitDimension.COUNT, BigDecimal.ONE, true, 5)
        )
        every { unitRepository.observeAll() } returns flowOf(units)
        val aliases = mapOf(
            "mass_lb" to listOf("lb", "lbs", "pound", "pounds"),
            "mass_oz" to listOf("oz", "ounce", "ounces"),
            "mass_kg" to listOf("kg", "kgs", "kilogram", "kilograms"),
            "volume_ml" to listOf("ml", "milliliter", "milliliters"),
            "volume_gallon_us" to listOf("gal", "gallon", "gallons"),
            "count_each" to listOf("ea", "each", "piece", "pieces")
        )

        aliases.forEach { (expectedId, values) ->
            values.forEach { value ->
                val document = service.processCsv(restaurantId, listOf(mapOf(
                    CsvParser.HEADER_INGREDIENT_NAME to "Item-$expectedId-$value",
                    CsvParser.HEADER_BASE_UNIT to value
                )))
                assertThat(document.rows.single().normalizedData?.resolvedBaseUnitId?.value).isEqualTo(expectedId)
            }
        }
    }

    @Test
    fun `incompatible count unit issue uses unit symbols`() = runTest {
        val each = UnitOfMeasure(
            id = com.venkoi.cuentame.core.common.ids.UnitId("each"), name = "Each", symbol = "ea",
            dimension = UnitDimension.COUNT, factorToCanonical = BigDecimal.ONE, isSystem = true, sortOrder = 1
        )
        val lb = UnitOfMeasure(
            id = com.venkoi.cuentame.core.common.ids.UnitId("lb"), name = "Pound", symbol = "lb",
            dimension = UnitDimension.MASS, factorToCanonical = BigDecimal.ONE, isSystem = true, sortOrder = 0
        )
        every { unitRepository.observeAll() } returns flowOf(listOf(lb, each))

        val result = service.processCsv(
            restaurantId,
            listOf(row() + (CsvParser.HEADER_COUNT_UNIT to "ea"))
        )

        val issue = result.rows.single().issues.single { it.code == CsvImportIssueCode.INCOMPATIBLE_COUNT_UNIT }
        assertThat(issue.parameters).containsExactly("ea", "lb").inOrder()
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
        assertThat(result.rows[1].issues.map { it.code }).contains(CsvImportIssueCode.DUPLICATE_INGREDIENT_NAME_IN_FILE)
    }

    @Test
    fun `detect archived category conflict error`() = runTest {
        val archivedCategory = com.venkoi.cuentame.core.model.ingredient.IngredientCategory(
            id = com.venkoi.cuentame.core.common.ids.IngredientCategoryId("cat-1"),
            restaurantId = restaurantId,
            name = "Archived Cat",
            normalizedName = "archived cat",
            sortOrder = 0,
            isActive = true,
            createdAt = java.time.Instant.EPOCH,
            updatedAt = java.time.Instant.EPOCH,
            deletedAt = java.time.Instant.now()
        )
        coEvery { categoryRepository.getAllCategoriesForRestaurant(restaurantId) } returns listOf(archivedCategory)

        val rawRows = listOf(
            mapOf(
                CsvParser.HEADER_INGREDIENT_NAME to "Tomato",
                CsvParser.HEADER_BASE_UNIT to "lb",
                CsvParser.HEADER_CATEGORY to "Archived Cat"
            )
        )
        
        val result = service.processCsv(restaurantId, rawRows)
        
        assertThat(result.rows[0].status).isEqualTo(CsvImportRowStatus.ERROR)
        assertThat(result.rows[0].issues.map { it.code }).contains(CsvImportIssueCode.CATEGORY_ARCHIVED)
    }

    @Test
    fun `active category wins over archived category with same normalized name`() = runTest {
        val archived = category("old", deletedAt = Instant.EPOCH)
        val active = category("active")
        coEvery { categoryRepository.getAllCategoriesForRestaurant(restaurantId) } returns listOf(archived, active)

        val result = service.processCsv(restaurantId, listOf(row(category = "Produce")))

        assertThat(result.rows.single().normalizedData?.resolvedCategoryId).isEqualTo(active.id)
        assertThat(result.rows.single().issues.map { it.code }).doesNotContain(CsvImportIssueCode.CATEGORY_ARCHIVED)
    }

    @Test
    fun `two active categories are a blocking data conflict`() = runTest {
        coEvery { categoryRepository.getAllCategoriesForRestaurant(restaurantId) } returns listOf(category("one"), category("two"))

        val result = service.processCsv(restaurantId, listOf(row(category = "Produce")))

        assertThat(result.rows.single().issues.map { it.code }).contains(CsvImportIssueCode.CATEGORY_DATA_CONFLICT)
        assertThat(result.rows.single().status).isEqualTo(CsvImportRowStatus.ERROR)
    }

    @Test
    fun `active supplier wins over archived supplier with same normalized name`() = runTest {
        val archived = supplier("old", deletedAt = Instant.EPOCH)
        val active = supplier("active")
        every { supplierRepository.observeSuppliers(restaurantId, includeArchived = true) } returns flowOf(listOf(archived, active))

        val result = service.processCsv(restaurantId, listOf(row(supplier = "Sysco")))

        assertThat(result.rows.single().normalizedData?.resolvedSupplierId).isEqualTo(active.id)
        assertThat(result.rows.single().issues.map { it.code }).doesNotContain(CsvImportIssueCode.SUPPLIER_ARCHIVED)
    }

    @Test
    fun `two active suppliers are a blocking data conflict`() = runTest {
        every { supplierRepository.observeSuppliers(restaurantId, includeArchived = true) } returns flowOf(listOf(supplier("one"), supplier("two")))

        val result = service.processCsv(restaurantId, listOf(row(supplier = "Sysco")))

        assertThat(result.rows.single().issues.map { it.code }).contains(CsvImportIssueCode.SUPPLIER_DATA_CONFLICT)
    }

    private fun row(category: String? = null, supplier: String? = null) = buildMap {
        put(CsvParser.HEADER_INGREDIENT_NAME, "Tomato")
        put(CsvParser.HEADER_BASE_UNIT, "lb")
        category?.let { put(CsvParser.HEADER_CATEGORY, it) }
        supplier?.let { put(CsvParser.HEADER_SUPPLIER, it) }
    }

    private fun category(id: String, deletedAt: Instant? = null) =
        com.venkoi.cuentame.core.model.ingredient.IngredientCategory(
            com.venkoi.cuentame.core.common.ids.IngredientCategoryId(id), restaurantId, "Produce", "produce", 0,
            true, Instant.EPOCH, Instant.EPOCH, deletedAt
        )

    private fun supplier(id: String, deletedAt: Instant? = null) =
        com.venkoi.cuentame.core.model.supplier.Supplier(
            com.venkoi.cuentame.core.common.ids.SupplierId(id), restaurantId, "Sysco", "sysco",
            isActive = true, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH, deletedAt = deletedAt
        )
}
