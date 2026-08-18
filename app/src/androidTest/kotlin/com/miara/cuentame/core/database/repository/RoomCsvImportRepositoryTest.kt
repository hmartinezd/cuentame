package com.miara.cuentame.core.database.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.UnitId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.domain.repository.ImportFailure
import com.miara.cuentame.core.domain.repository.ImportResult
import com.miara.cuentame.core.domain.service.StandardUnitConverter
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvIngredientImportDocument
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvIngredientImportRow
import com.miara.cuentame.feature.ingredients.csvimport.domain.NormalizedIngredientData
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvImportRowStatus
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvImportService
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvParser
import com.miara.cuentame.feature.ingredients.csvimport.domain.IngredientColumnMapper
import com.miara.cuentame.core.ocr.parser.matching.InventoryNormalization
import java.io.ByteArrayInputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class RoomCsvImportRepositoryTest {
    private lateinit var db: RestaurantInventoryDatabase
    private lateinit var repository: RoomCsvImportRepository
    private lateinit var importService: CsvImportService
    private val testIds = object : IdGenerator {
        private var counter = 0
        override fun newId(): String = "id-${counter++}"
    }
    private val testTime = object : TimeProvider { override fun now(): Instant = Instant.EPOCH }
    private val restId = RestaurantId("rest-1")

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RestaurantInventoryDatabase::class.java).build()
        repository = RoomCsvImportRepository(
            db,
            db.ingredientDao(),
            db.ingredientUnitOptionDao(),
            db.ingredientCategoryDao(),
            db.supplierDao(),
            db.supplierItemMappingDao(),
            db.ingredientCostProjectionDao(),
            db.unitDao(),
            db.restaurantDao(),
            db.inventoryAreaDao(),
            StandardUnitConverter(),
            testIds,
            testTime
        )
        runBlocking {
            db.restaurantDao().insert(com.miara.cuentame.core.database.entity.RestaurantEntity(restId.value, "R", "USD", "en-US", 0L, 0L, null))
            db.unitDao().insertSeedUnits(listOf(
                com.miara.cuentame.core.database.entity.UnitEntity("u1", "Pound", "lb", "MASS", BigDecimal.ONE, true, 0),
                com.miara.cuentame.core.database.entity.UnitEntity("u2", "Ounce", "oz", "MASS", BigDecimal("0.0625"), true, 1),
                com.miara.cuentame.core.database.entity.UnitEntity("mass_lb", "Pound", "lb", "MASS", BigDecimal.ONE, true, 2)
            ))
        }
        importService = CsvImportService(
            RoomIngredientRepository(
                db, db.ingredientDao(), db.ingredientUnitOptionDao(), db.unitDao(), db.restaurantDao(),
                db.ingredientCategoryDao(), db.preparationRecipeDao(), db.productionBatchDao(),
                StandardUnitConverter(), testIds, testTime
            ),
            RoomIngredientCategoryRepository(db, db.ingredientCategoryDao()),
            RoomInventoryAreaRepository(db, db.inventoryAreaDao(), db.restaurantDao(), db.productionBatchDao()),
            RoomSupplierRepository(db.supplierDao(), db.restaurantDao(), testIds, testTime),
            RoomUnitRepository(db.unitDao()),
            RoomSupplierItemMappingRepository(
                db.supplierItemMappingDao(), db.supplierDao(), db.ingredientDao(), db.ingredientUnitOptionDao(),
                db.inventoryAreaDao(), testIds
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun commitImport_succeedsForValidData() = runBlocking {
        val row = CsvIngredientImportRow(
            rowNumber = 2,
            rawData = emptyMap(),
            normalizedData = NormalizedIngredientData(
                name = "Tomato",
                sku = "TOM001",
                categoryName = "Produce",
                resolvedCategoryId = null,
                baseUnitName = "lb",
                resolvedBaseUnitId = UnitId("u1"),
                countUnitName = null,
                resolvedCountUnitId = null,
                purchasePackageName = "Case",
                packageConversionFactor = BigDecimal("25"),
                defaultAreaName = null,
                resolvedDefaultAreaId = null,
                supplierName = "Sysco",
                resolvedSupplierId = null,
                vendorItemCode = "123",
                currentCostPerBaseUnit = BigDecimal("1.5"),
                reorderPointBase = BigDecimal("10")
            ),
            issues = emptyList(),
            status = CsvImportRowStatus.READY,
            isIncluded = true
        )
        
        val doc = CsvIngredientImportDocument(listOf(row))
        val result = repository.commitImport(restId, doc)
        
        assertThat(result).isInstanceOf(ImportResult.Success::class.java)
        val success = result as ImportResult.Success
        assertThat(success.ingredientsCreated).isEqualTo(1)
        assertThat(success.categoriesCreated).isEqualTo(1)
        assertThat(success.suppliersCreated).isEqualTo(1)
        
        val ingredients = db.ingredientDao().getActiveIngredients(restId.value)
        assertThat(ingredients).hasSize(1)
        assertThat(ingredients[0].name).isEqualTo("Tomato")
        assertThat(ingredients[0].sku).isEqualTo("TOM001")
    }

    @Test
    fun commitImport_isAtomicOnFailure() = runBlocking {
        // First, check counts are zero
        assertThat(db.ingredientDao().getActiveIngredients(restId.value)).isEmpty()

        val validRow = CsvIngredientImportRow(
            rowNumber = 2,
            rawData = emptyMap(),
            normalizedData = NormalizedIngredientData(
                name = "Tomato",
                sku = null,
                categoryName = null,
                resolvedCategoryId = null,
                baseUnitName = "lb",
                resolvedBaseUnitId = UnitId("u1"),
                countUnitName = null,
                resolvedCountUnitId = null,
                purchasePackageName = null,
                packageConversionFactor = null,
                defaultAreaName = null,
                resolvedDefaultAreaId = null,
                supplierName = null,
                resolvedSupplierId = null,
                vendorItemCode = null,
                currentCostPerBaseUnit = null,
                reorderPointBase = null
            ),
            issues = emptyList(),
            status = CsvImportRowStatus.READY,
            isIncluded = true
        )

        // A row that will fail during re-validation or insertion
        val invalidRow = CsvIngredientImportRow(
            rowNumber = 3,
            rawData = emptyMap(),
            normalizedData = NormalizedIngredientData(
                name = "Onion",
                sku = null,
                categoryName = null,
                resolvedCategoryId = null,
                baseUnitName = "invalid",
                resolvedBaseUnitId = UnitId("invalid"), // This will cause TOCTOU StateChanged failure
                countUnitName = null,
                resolvedCountUnitId = null,
                purchasePackageName = null,
                packageConversionFactor = null,
                defaultAreaName = null,
                resolvedDefaultAreaId = null,
                supplierName = null,
                resolvedSupplierId = null,
                vendorItemCode = null,
                currentCostPerBaseUnit = null,
                reorderPointBase = null
            ),
            issues = emptyList(),
            status = CsvImportRowStatus.READY,
            isIncluded = true
        )

        val doc = CsvIngredientImportDocument(listOf(validRow, invalidRow))
        val result = repository.commitImport(restId, doc)
        
        assertThat(result).isInstanceOf(ImportResult.Failure::class.java)
        assertThat((result as ImportResult.Failure).failure).isEqualTo(ImportFailure.InvalidPlan)
        
        // Verify zero ingredients created
        assertThat(db.ingredientDao().getActiveIngredients(restId.value)).isEmpty()
        assertThat(db.ingredientCategoryDao().getActiveIds(restId.value)).isEmpty()
        assertThat(db.supplierDao().searchSuppliers(restId.value, "")).isEmpty()
    }

    @Test
    fun previewAndEdit_doesNotCreateDatabaseWrites() = runBlocking {
        val parsed = CsvParser().parse(ByteArrayInputStream(
            "ingredient_name,base_unit,category,supplier,vendor_item_code\nTomato,lb,Produce,Sysco,ABC-1".toByteArray()
        )) as CsvParser.ParseResult.Success
        val preview = importService.processCsv(restId, parsed.rows)
        val editedRows = parsed.rows.map { it + (CsvParser.HEADER_INGREDIENT_NAME to "Roma Tomato") }
        val edited = importService.processCsv(restId, editedRows)
        val skipped = edited.copy(rows = edited.rows.map { it.copy(isIncluded = false) })
        val unskipped = skipped.copy(rows = skipped.rows.map { it.copy(isIncluded = true) })

        assertThat(preview.rows).hasSize(1)
        assertThat(edited.rows.single().normalizedData?.name).isEqualTo("Roma Tomato")
        assertThat(skipped.rows.single().isIncluded).isFalse()
        assertThat(unskipped.rows.single().isIncluded).isTrue()
        assertThat(db.ingredientDao().getActiveIngredients(restId.value)).isEmpty()
        assertThat(db.ingredientCategoryDao().observeAllCategories().first()).isEmpty()
        assertThat(db.supplierDao().observeAllSuppliers(restId.value).first()).isEmpty()
        assertThat(db.supplierItemMappingDao().getAllMappingsSync(restId.value)).isEmpty()
        assertThat(tableCount("ingredient_unit_options")).isEqualTo(0)
        assertThat(tableCount("ingredient_cost_projection")).isEqualTo(0)
        assertThat(tableCount("purchase_receipts")).isEqualTo(0)
        assertThat(tableCount("purchase_lines")).isEqualTo(0)
        assertThat(tableCount("inventory_movements")).isEqualTo(0)
    }

    @Test
    fun commitImport_120IngredientsPilot_succeeds() = runBlocking {
        val rows = (1..120).map { i ->
            CsvIngredientImportRow(
                rowNumber = i + 1,
                rawData = emptyMap(),
                normalizedData = NormalizedIngredientData(
                    name = "Ingredient $i",
                    sku = "SKU-$i",
                    categoryName = "Category ${i % 5}",
                    resolvedCategoryId = null,
                    baseUnitName = "lb",
                    resolvedBaseUnitId = UnitId("u1"),
                    countUnitName = null,
                    resolvedCountUnitId = null,
                    purchasePackageName = "Pack $i",
                    packageConversionFactor = BigDecimal(i.toString()),
                    defaultAreaName = null,
                    resolvedDefaultAreaId = null,
                    supplierName = "Supplier ${i % 3}",
                    resolvedSupplierId = null,
                    vendorItemCode = "VC-$i",
                    currentCostPerBaseUnit = BigDecimal("1.0"),
                    reorderPointBase = BigDecimal("5.0")
                ),
                issues = emptyList(),
                status = CsvImportRowStatus.READY,
                isIncluded = true
            )
        }
        
        val doc = CsvIngredientImportDocument(rows)
        val result = repository.commitImport(restId, doc)
        
        assertThat(result).isInstanceOf(ImportResult.Success::class.java)
        val success = result as ImportResult.Success
        assertThat(success.ingredientsCreated).isEqualTo(120)
        assertThat(success.categoriesCreated).isEqualTo(5)
        assertThat(success.suppliersCreated).isEqualTo(3)
        
        assertThat(db.ingredientDao().getActiveIngredients(restId.value)).hasSize(120)
    }

    @Test
    fun fullPipeline_120CsvRows_previewIsReadOnly_thenCommitSucceeds() = runBlocking {
        db.ingredientCategoryDao().upsert(com.miara.cuentame.core.database.entity.IngredientCategoryEntity(
            "existing-category", restId.value, "Produce", "produce", 0, true, 0, 0, null
        ))
        db.supplierDao().insert(com.miara.cuentame.core.database.entity.SupplierEntity(
            "existing-supplier", restId.value, "Sysco", "sysco", null, null, null, true, 0, 0, null
        ))
        db.inventoryAreaDao().upsert(com.miara.cuentame.core.database.entity.InventoryAreaEntity(
            "dry-area", restId.value, "Dry Storage", "dry storage", 0, true, 0, 0, null
        ))
        val csv = buildString {
            append("ingredient_name,sku,category,base_unit,count_unit,purchase_package,package_conversion_factor,default_area,supplier,vendor_item_code,current_cost_per_base_unit,reorder_point_base\n")
            repeat(120) { index ->
                val number = index + 1
                val category = if (number % 3 == 0) "Produce" else "New Category ${number % 4}"
                val supplier = if (number % 3 == 0) "Sysco" else "New Supplier ${number % 2}"
                val vendorCode = if (number == 1) "  vc.item-1  " else "VC-$number"
                append("Ingredient $number,SKU-$number,$category,Pound,oz,Case,${number + 1},Dry Storage,$supplier,$vendorCode,1.25,5\n")
            }
        }
        val parsed = CsvParser().parse(ByteArrayInputStream(csv.toByteArray())) as CsvParser.ParseResult.Success
        val document = importService.processCsv(restId, parsed.rows)

        assertThat(document.rows).hasSize(120)
        assertThat(document.rows.none { it.status == CsvImportRowStatus.ERROR }).isTrue()
        assertThat(tableCount("ingredients")).isEqualTo(0)
        assertThat(tableCount("ingredient_categories")).isEqualTo(1)
        assertThat(tableCount("suppliers")).isEqualTo(1)

        val result = repository.commitImport(restId, document) as ImportResult.Success

        assertThat(result.ingredientsCreated).isEqualTo(120)
        assertThat(result.categoriesCreated).isEqualTo(4)
        assertThat(result.suppliersCreated).isEqualTo(2)
        assertThat(result.mappingsCreated).isEqualTo(120)
        assertThat(tableCount("ingredients")).isEqualTo(120)
        assertThat(tableCount("ingredient_unit_options")).isEqualTo(360)
        assertThat(tableCount("ingredient_categories")).isEqualTo(5)
        assertThat(tableCount("suppliers")).isEqualTo(3)
        assertThat(tableCount("supplier_item_mappings")).isEqualTo(120)
        assertThat(tableCount("ingredient_cost_projection")).isEqualTo(120)
        assertThat(tableCount("purchase_receipts")).isEqualTo(0)
        assertThat(tableCount("purchase_lines")).isEqualTo(0)
        assertThat(tableCount("inventory_movements")).isEqualTo(0)
        db.supplierItemMappingDao().getAllMappingsSync(restId.value).forEach { mapping ->
            mapping.sourceVendorCode?.let { sourceVendorCode ->
                assertThat(mapping.normalizedKey)
                    .isEqualTo(InventoryNormalization.normalizeVendorCode(sourceVendorCode))
            }
        }
        val ingredients = db.ingredientDao().getActiveIngredients(restId.value)
        assertThat(ingredients.all { it.defaultAreaId == "dry-area" }).isTrue()
        assertThat(ingredients.all { it.reorderPointBase == BigDecimal("5") }).isTrue()
        ingredients.forEach { ingredient ->
            val options = db.ingredientUnitOptionDao().getActiveOptions(ingredient.id)
            assertThat(options.count { it.isBase }).isEqualTo(1)
            assertThat(options.count { it.isDefaultCount }).isEqualTo(1)
            assertThat(options.count { it.isDefaultPurchase }).isEqualTo(1)
            assertThat(options.single { it.isDefaultCount }.factorToBase).isEqualTo(BigDecimal("0.0625"))
        }
        assertThat(db.ingredientCostProjectionDao().getAll().all { it.averageUnitCostBase == "1.25" }).isTrue()
        val mappings = db.supplierItemMappingDao().getAllMappingsSync(restId.value)
        assertThat(mappings.all { it.unitOptionId != null && it.inventoryAreaId == "dry-area" }).isTrue()
    }

    @Test
    fun realisticArbitraryHeaderCsv_mapsPreviewsWithoutWrites_thenCommitsExactly() = runBlocking {
        val csv = """Item,Department,UOM,Pack,Pack Qty,Vendor,Item #,Cost
Chicken Breast,Meat,lbs,Case,40,Sysco,12345,2.45
Roma Tomatoes,Produce,lb,Case,25,FreshPoint,ABC12,1.30"""
        val parsed = CsvParser().parse(ByteArrayInputStream(csv.toByteArray())) as CsvParser.ParseResult.Success
        val mapping = IngredientColumnMapper.suggest(parsed.table)
        assertThat(mapping.isValid).isTrue()

        val document = importService.processCsv(restId, IngredientColumnMapper.toCanonicalRows(parsed.table, mapping))

        assertThat(document.rows).hasSize(2)
        assertThat(document.rows.all { it.status != CsvImportRowStatus.ERROR }).isTrue()
        assertThat(document.rows.map { it.normalizedData!!.resolvedBaseUnitId!!.value })
            .containsExactly("mass_lb", "mass_lb")
        assertThat(tableCount("ingredient_categories")).isEqualTo(0)
        assertThat(tableCount("suppliers")).isEqualTo(0)

        assertThat(repository.commitImport(restId, document)).isInstanceOf(ImportResult.Success::class.java)

        val ingredients = db.ingredientDao().getActiveIngredients(restId.value).associateBy { it.name }
        assertThat(ingredients.keys).containsExactly("Chicken Breast", "Roma Tomatoes")
        assertThat(ingredients.values.all { it.baseUnitId == "mass_lb" }).isTrue()
        assertThat(db.ingredientCategoryDao().getAllCategoriesForRestaurant(restId.value).map { it.name })
            .containsExactly("Meat", "Produce")
        assertThat(db.supplierDao().observeAllSuppliers(restId.value).first().map { it.name })
            .containsExactly("Sysco", "FreshPoint")
        val mappings = db.supplierItemMappingDao().getAllMappingsSync(restId.value).associateBy { it.sourceVendorCode }
        assertThat(mappings.keys).containsExactly("12345", "ABC12")
        assertThat(db.ingredientUnitOptionDao().getActiveOptions(ingredients.getValue("Chicken Breast").id)
            .single { it.isDefaultPurchase }.factorToBase).isEqualTo(BigDecimal("40"))
        assertThat(db.ingredientUnitOptionDao().getActiveOptions(ingredients.getValue("Roma Tomatoes").id)
            .single { it.isDefaultPurchase }.factorToBase).isEqualTo(BigDecimal("25"))
        val costs = db.ingredientCostProjectionDao().getAll().associateBy { it.ingredientId }
        assertThat(costs.getValue(ingredients.getValue("Chicken Breast").id).averageUnitCostBase).isEqualTo("2.45")
        assertThat(costs.getValue(ingredients.getValue("Roma Tomatoes").id).averageUnitCostBase).isEqualTo("1.30")
    }

    @Test
    fun defaultArea_reusesActiveAreas_andUnknownAreaDoesNotCreateOne() = runBlocking {
        listOf("Walk-in", "Dry Storage").forEachIndexed { index, name ->
            db.inventoryAreaDao().upsert(com.miara.cuentame.core.database.entity.InventoryAreaEntity(
                "area-$index", restId.value, name, name.lowercase(), index, true, 0, 0, null
            ))
        }
        val knownRows = listOf("Walk-in", "Dry Storage").mapIndexed { index, area -> mapOf(
            CsvParser.HEADER_INGREDIENT_NAME to "Item $index",
            CsvParser.HEADER_BASE_UNIT to "lb",
            CsvParser.HEADER_DEFAULT_AREA to area
        ) }
        val knownDocument = importService.processCsv(restId, knownRows)

        assertThat(knownDocument.rows.map { it.normalizedData!!.resolvedDefaultAreaId!!.value })
            .containsExactly("area-0", "area-1").inOrder()
        assertThat(repository.commitImport(restId, knownDocument)).isInstanceOf(ImportResult.Success::class.java)
        assertThat(db.ingredientDao().getActiveIngredients(restId.value).map { it.defaultAreaId })
            .containsExactly("area-0", "area-1")

        val unknown = importService.processCsv(restId, listOf(mapOf(
            CsvParser.HEADER_INGREDIENT_NAME to "Garage Item",
            CsvParser.HEADER_BASE_UNIT to "lb",
            CsvParser.HEADER_DEFAULT_AREA to "Garage"
        )))
        assertThat(unknown.rows.single().issues.map { it.code })
            .contains(com.miara.cuentame.feature.ingredients.csvimport.domain.CsvImportIssueCode.UNKNOWN_AREA)
        assertThat(db.inventoryAreaDao().getActiveAreasSync(restId.value).map { it.name })
            .containsExactly("Walk-in", "Dry Storage")
    }

    private fun tableCount(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
}
