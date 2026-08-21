package com.venkoi.cuentame.feature.ingredients.csvimport.ui

import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.domain.repository.CsvImportRepository
import com.venkoi.cuentame.core.domain.repository.ImportResult
import com.venkoi.cuentame.core.domain.repository.RestaurantRepository
import com.venkoi.cuentame.feature.ingredients.csvimport.domain.CsvIngredientImportDocument
import com.venkoi.cuentame.feature.ingredients.csvimport.domain.CsvImportService
import com.venkoi.cuentame.feature.ingredients.csvimport.domain.CsvParser
import com.venkoi.cuentame.feature.ingredients.csvimport.domain.CsvTemplateGenerator
import com.venkoi.cuentame.feature.ingredients.csvimport.domain.CsvImportRowStatus
import com.venkoi.cuentame.feature.ingredients.csvimport.domain.CsvIngredientImportRow
import com.venkoi.cuentame.feature.ingredients.csvimport.domain.CsvSourceColumn
import com.venkoi.cuentame.feature.ingredients.csvimport.domain.CsvSourceTable
import com.venkoi.cuentame.feature.ingredients.csvimport.domain.IngredientImportField
import com.venkoi.cuentame.core.domain.repository.ImportFailure
import io.mockk.coEvery
import io.mockk.every
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

@OptIn(ExperimentalCoroutinesApi::class)
class CsvImportViewModelTest {
    private val csvParser = mockk<CsvParser>()
    private val importService = mockk<CsvImportService>()
    private val importRepository = mockk<CsvImportRepository>()
    private val restaurantRepository = mockk<RestaurantRepository>()
    private val templateGenerator = mockk<CsvTemplateGenerator>()
    
    private lateinit var viewModel: CsvImportViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CsvImportViewModel(
            csvParser,
            importService,
            importRepository,
            restaurantRepository,
            templateGenerator
        )
        viewModel.parsingDispatcher = testDispatcher
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is empty`() {
        val state = viewModel.uiState.value
        assertThat(state.document == null).isTrue()
        assertThat(state.isParsing).isFalse()
        assertThat(state.isCommitting).isFalse()
    }

    @Test
    fun `valid alias mapping creates preview`() = runTest {
        val restaurant = mockk<com.venkoi.cuentame.core.model.restaurant.Restaurant>()
        val restaurantId = com.venkoi.cuentame.core.common.ids.RestaurantId("rest-1")
        every { restaurant.id } returns restaurantId
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        
        val table = validSourceTable("Chicken" to "lbs")
        every { csvParser.parse(any()) } returns CsvParser.ParseResult.Success(table)
        
        val doc = CsvIngredientImportDocument(emptyList())
        coEvery { importService.processCsv(any(), any()) } returns doc
        
        viewModel.loadCsv(ByteArrayInputStream("".toByteArray()))
        testDispatcher.scheduler.advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertThat(state.sourceTable).isEqualTo(table)
        assertThat(state.columnMapping).isNotNull()
        assertThat(state.columnMapping!!.sourceToTarget[0]).isEqualTo(IngredientImportField.INGREDIENT_NAME)
        assertThat(state.columnMapping!!.sourceToTarget[1]).isEqualTo(IngredientImportField.BASE_UNIT)
        assertThat(state.document == doc).isTrue()
        assertThat(state.isParsing).isFalse()
    }

    @Test
    fun `invalid mapping retains source and reports missing required fields without preview`() = runTest {
        val table = sourceTable(listOf("Mystery", "Something"), listOf("Chicken", "lbs"))
        every { csvParser.parse(any()) } returns CsvParser.ParseResult.Success(table)

        viewModel.loadCsv(ByteArrayInputStream(byteArrayOf()))
        assertThat(viewModel.uiState.value.document).isNull()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.sourceTable).isEqualTo(table)
        assertThat(state.document).isNull()
        assertThat(state.columnMapping!!.missingRequiredFields)
            .containsExactly(IngredientImportField.INGREDIENT_NAME, IngredientImportField.BASE_UNIT)
        coVerify(exactly = 0) { importService.processCsv(any(), any()) }
    }

    @Test
    fun `manual mapping generates preview from canonical rows`() = runTest {
        stubRestaurant()
        val table = sourceTable(listOf("Mystery", "Something"), listOf("Chicken", "lbs"))
        val doc = CsvIngredientImportDocument(emptyList())
        every { csvParser.parse(any()) } returns CsvParser.ParseResult.Success(table)
        coEvery { importService.processCsv(any(), any()) } returns doc

        viewModel.loadCsv(ByteArrayInputStream(byteArrayOf()))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.updateMapping(0, IngredientImportField.INGREDIENT_NAME)
        viewModel.updateMapping(1, IngredientImportField.BASE_UNIT)
        viewModel.previewMappedCsv()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.document).isEqualTo(doc)
        coVerify { importService.processCsv(any(), match { rows ->
            rows.single()[CsvParser.HEADER_INGREDIENT_NAME] == "Chicken" &&
                rows.single()[CsvParser.HEADER_BASE_UNIT] == "lbs"
        }) }
    }

    @Test
    fun `confirmImport single-flight guard`() = runTest {
        val restaurant = mockk<com.venkoi.cuentame.core.model.restaurant.Restaurant>()
        every { restaurant.id } returns com.venkoi.cuentame.core.common.ids.RestaurantId("rest-1")
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        
        val doc = CsvIngredientImportDocument(listOf(
            CsvIngredientImportRow(1, emptyMap(), null, emptyList(), CsvImportRowStatus.READY, true)
        ))
        
        // Use manual update to set document
        every { csvParser.parse(any()) } returns CsvParser.ParseResult.Success(validSourceTable())
        coEvery { importService.processCsv(any(), any()) } returns doc
        viewModel.loadCsv(ByteArrayInputStream("".toByteArray()))
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { importRepository.commitImport(any(), any()) } coAnswers {
            kotlinx.coroutines.delay(1000)
            ImportResult.Success(1, 0, 0, 0, 0)
        }

        viewModel.confirmImport()
        viewModel.confirmImport() // Second call should be ignored
        testDispatcher.scheduler.runCurrent()
        assertThat(viewModel.uiState.value.isCommitting).isTrue()
        
        testDispatcher.scheduler.advanceTimeBy(2000)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertThat(viewModel.uiState.value.isCommitting).isFalse()
        // Repository should be called only once
        io.mockk.coVerify(exactly = 1) { importRepository.commitImport(any(), any()) }
    }

    @Test
    fun `parser failure clears parsing state`() = runTest {
        every { csvParser.parse(any()) } throws java.io.IOException("read failed")

        viewModel.loadCsv(ByteArrayInputStream(byteArrayOf()))
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.isParsing).isFalse()
        assertThat(viewModel.uiState.value.parseError).isEqualTo(CsvParser.ParseErrorType.READ_FAILURE)
    }

    @Test
    fun `restaurant unavailable clears parsing state`() = runTest {
        val table = validSourceTable()
        every { csvParser.parse(any()) } returns CsvParser.ParseResult.Success(table)
        coEvery { restaurantRepository.getRestaurant() } returns null

        viewModel.loadCsv(ByteArrayInputStream(byteArrayOf()))
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.isParsing).isFalse()
        assertThat(viewModel.uiState.value.sourceTable).isEqualTo(table)
        assertThat(viewModel.uiState.value.columnMapping!!.isValid).isTrue()
        assertThat(viewModel.uiState.value.document).isNull()
        assertThat(viewModel.uiState.value.importResult)
            .isEqualTo(ImportResult.Failure(ImportFailure.RestaurantUnavailable))
    }

    @Test
    fun `header-only valid mapped source creates empty preview that cannot commit`() = runTest {
        stubRestaurant()
        every { csvParser.parse(any()) } returns CsvParser.ParseResult.Success(
            sourceTable(listOf("Item", "UOM"))
        )
        coEvery { importService.processCsv(any(), emptyList()) } returns CsvIngredientImportDocument(emptyList())

        viewModel.loadCsv(ByteArrayInputStream(byteArrayOf()))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.confirmImport()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.columnMapping!!.isValid).isTrue()
        assertThat(viewModel.uiState.value.document?.rows).isEmpty()
        coVerify(exactly = 0) { importRepository.commitImport(any(), any()) }
    }

    @Test
    fun `new malformed file clears old preview and cannot confirm`() = runTest {
        val restaurant = mockk<com.venkoi.cuentame.core.model.restaurant.Restaurant>()
        every { restaurant.id } returns com.venkoi.cuentame.core.common.ids.RestaurantId("rest-1")
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        val tableA = validSourceTable()
        val rowsA = canonicalRows("Tomato" to "lb")
        val docA = CsvIngredientImportDocument(listOf(
            CsvIngredientImportRow(2, rowsA.single(), null, emptyList(), CsvImportRowStatus.READY, true)
        ))
        every { csvParser.parse(any()) } returnsMany listOf(
            CsvParser.ParseResult.Success(tableA),
            CsvParser.ParseResult.Error(CsvParser.ParseErrorType.MALFORMED_CSV)
        )
        coEvery { importService.processCsv(any(), any()) } returns docA

        viewModel.loadCsv(ByteArrayInputStream(byteArrayOf()))
        assertThat(viewModel.uiState.value.document).isNull()
        testDispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.uiState.value.document).isEqualTo(docA)

        viewModel.loadCsv(ByteArrayInputStream(byteArrayOf()))
        assertThat(viewModel.uiState.value.document).isNull()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.confirmImport()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.document).isNull()
        assertThat(viewModel.uiState.value.parseError).isEqualTo(CsvParser.ParseErrorType.MALFORMED_CSV)
        coVerify(exactly = 0) { importRepository.commitImport(any(), any()) }
    }

    @Test
    fun `StateChanged remains visible until refresh replaces preview and preserves selections`() = runTest {
        val restaurant = mockk<com.venkoi.cuentame.core.model.restaurant.Restaurant>()
        val restaurantId = com.venkoi.cuentame.core.common.ids.RestaurantId("rest-1")
        every { restaurant.id } returns restaurantId
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        val rawRows = canonicalRows("Tomato" to "lb", "Onion" to "lb")
        val initial = CsvIngredientImportDocument(rawRows.mapIndexed { index, raw ->
            CsvIngredientImportRow(index + 2, raw, null, emptyList(), CsvImportRowStatus.READY, true)
        })
        val refreshed = CsvIngredientImportDocument(rawRows.mapIndexed { index, raw ->
            CsvIngredientImportRow(index + 2, raw, null, emptyList(), CsvImportRowStatus.READY, true)
        })
        every { csvParser.parse(any()) } returns CsvParser.ParseResult.Success(
            validSourceTable("Tomato" to "lb", "Onion" to "lb")
        )
        coEvery { importService.processCsv(any(), any()) } returnsMany listOf(initial, refreshed)
        coEvery { importRepository.commitImport(any(), any()) } returns ImportResult.Failure(ImportFailure.StateChanged)

        viewModel.loadCsv(ByteArrayInputStream(byteArrayOf()))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleRowSelection(3)
        viewModel.confirmImport()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.document).isNotNull()
        assertThat(viewModel.uiState.value.importResult).isEqualTo(ImportResult.Failure(ImportFailure.StateChanged))

        viewModel.refreshPreview()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { importService.processCsv(restaurantId, any()) }
        assertThat(viewModel.uiState.value.document?.rows?.single { it.rowNumber == 3 }?.isIncluded).isFalse()
        assertThat(viewModel.uiState.value.importResult).isNull()
    }

    @Test
    fun `refresh row error blocks confirmation`() = runTest {
        val restaurant = mockk<com.venkoi.cuentame.core.model.restaurant.Restaurant>()
        every { restaurant.id } returns com.venkoi.cuentame.core.common.ids.RestaurantId("rest-1")
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        val raw = canonicalRows("Tomato" to "lb").single()
        val initial = CsvIngredientImportDocument(listOf(CsvIngredientImportRow(2, raw, null, emptyList(), CsvImportRowStatus.READY, true)))
        val refreshed = CsvIngredientImportDocument(listOf(CsvIngredientImportRow(2, raw, null, emptyList(), CsvImportRowStatus.ERROR, true)))
        every { csvParser.parse(any()) } returns CsvParser.ParseResult.Success(validSourceTable())
        coEvery { importService.processCsv(any(), any()) } returnsMany listOf(initial, refreshed)
        coEvery { importRepository.commitImport(any(), any()) } returns ImportResult.Failure(ImportFailure.StateChanged)

        viewModel.loadCsv(ByteArrayInputStream(byteArrayOf()))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.confirmImport()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.refreshPreview()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.confirmImport()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.document?.rows?.single()?.status).isEqualTo(CsvImportRowStatus.ERROR)
        coVerify(exactly = 1) { importRepository.commitImport(any(), any()) }
    }

    @Test
    fun `skipping error enables confirmation and reincluding blocks it`() = runTest {
        val restaurant = mockk<com.venkoi.cuentame.core.model.restaurant.Restaurant>()
        every { restaurant.id } returns com.venkoi.cuentame.core.common.ids.RestaurantId("rest-1")
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        val rawRows = canonicalRows("Bad" to "lb", "Good" to "lb")
        val errorRow = CsvIngredientImportRow(2, rawRows[0], null, emptyList(), CsvImportRowStatus.ERROR, true)
        val readyRow = CsvIngredientImportRow(3, rawRows[1], null, emptyList(), CsvImportRowStatus.READY, true)
        every { csvParser.parse(any()) } returns CsvParser.ParseResult.Success(
            validSourceTable("Bad" to "lb", "Good" to "lb")
        )
        coEvery { importService.processCsv(any(), any()) } returns CsvIngredientImportDocument(listOf(errorRow, readyRow))
        coEvery { importRepository.commitImport(any(), any()) } returns ImportResult.Success(1, 0, 0, 0, 1)
        viewModel.loadCsv(ByteArrayInputStream(byteArrayOf()))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.confirmImport()
        testDispatcher.scheduler.advanceUntilIdle()
        io.mockk.coVerify(exactly = 0) { importRepository.commitImport(any(), any()) }

        viewModel.toggleRowSelection(2)
        viewModel.confirmImport()
        testDispatcher.scheduler.advanceUntilIdle()
        io.mockk.coVerify(exactly = 1) { importRepository.commitImport(any(), any()) }

        viewModel.resetImportResult()
        viewModel.toggleRowSelection(2)
        viewModel.confirmImport()
        testDispatcher.scheduler.advanceUntilIdle()
        io.mockk.coVerify(exactly = 1) { importRepository.commitImport(any(), any()) }
    }

    @Test
    fun `slower File A preview cannot overwrite File B`() = runTest {
        stubRestaurant()
        val tableA = validSourceTable("File A" to "lb")
        val tableB = validSourceTable("File B" to "lb")
        val docA = documentNamed("File A")
        val docB = documentNamed("File B")
        val releaseA = CompletableDeferred<Unit>()
        every { csvParser.parse(any()) } returnsMany listOf(
            CsvParser.ParseResult.Success(tableA), CsvParser.ParseResult.Success(tableB)
        )
        coEvery { importService.processCsv(any(), any()) } coAnswers {
            if (secondArg<List<Map<String, String>>>().single()[CsvParser.HEADER_INGREDIENT_NAME] == "File A") {
                releaseA.await()
                docA
            } else docB
        }

        viewModel.loadCsv(ByteArrayInputStream(byteArrayOf()))
        testDispatcher.scheduler.runCurrent()
        viewModel.loadCsv(ByteArrayInputStream(byteArrayOf()))
        testDispatcher.scheduler.runCurrent()
        assertThat(viewModel.uiState.value.document).isEqualTo(docB)

        releaseA.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.sourceTable).isEqualTo(tableB)
        assertThat(viewModel.uiState.value.columnMapping!!.sourceToTarget[0])
            .isEqualTo(IngredientImportField.INGREDIENT_NAME)
        assertThat(viewModel.uiState.value.document).isEqualTo(docB)
    }

    @Test
    fun `stale mapping preview cannot overwrite newer mapping preview`() = runTest {
        stubRestaurant()
        val table = sourceTable(listOf("Item", "Product", "UOM"), listOf("Mapping A", "Mapping B", "lb"))
        val docA = documentNamed("Mapping A")
        val docB = documentNamed("Mapping B")
        val releaseA = CompletableDeferred<Unit>()
        every { csvParser.parse(any()) } returns CsvParser.ParseResult.Success(table)
        coEvery { importService.processCsv(any(), any()) } coAnswers {
            when (secondArg<List<Map<String, String>>>().single()[CsvParser.HEADER_INGREDIENT_NAME]) {
                "Mapping A" -> { releaseA.await(); docA }
                else -> docB
            }
        }

        viewModel.loadCsv(ByteArrayInputStream(byteArrayOf()))
        testDispatcher.scheduler.runCurrent()
        viewModel.updateMapping(1, IngredientImportField.INGREDIENT_NAME)
        viewModel.previewMappedCsv()
        testDispatcher.scheduler.runCurrent()
        assertThat(viewModel.uiState.value.document).isEqualTo(docB)

        releaseA.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.uiState.value.columnMapping!!.sourceToTarget[1])
            .isEqualTo(IngredientImportField.INGREDIENT_NAME)
        assertThat(viewModel.uiState.value.document).isEqualTo(docB)
    }

    @Test
    fun `change mapping preserves parsed source and mapping and can preview again`() = runTest {
        stubRestaurant()
        val table = validSourceTable("Chicken" to "lb")
        val first = documentNamed("first")
        val second = documentNamed("second")
        every { csvParser.parse(any()) } returns CsvParser.ParseResult.Success(table)
        coEvery { importService.processCsv(any(), any()) } returnsMany listOf(first, second)

        viewModel.loadCsv(ByteArrayInputStream(byteArrayOf()))
        testDispatcher.scheduler.advanceUntilIdle()
        val mapping = viewModel.uiState.value.columnMapping
        viewModel.changeMapping()

        assertThat(viewModel.uiState.value.document).isNull()
        assertThat(viewModel.uiState.value.sourceTable).isSameInstanceAs(table)
        assertThat(viewModel.uiState.value.columnMapping).isSameInstanceAs(mapping)

        viewModel.previewMappedCsv()
        testDispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.uiState.value.document).isEqualTo(second)
        io.mockk.verify(exactly = 1) { csvParser.parse(any()) }
    }

    @Test
    fun `stale refresh cannot overwrite newly loaded file`() = runTest {
        stubRestaurant()
        val tableA = validSourceTable("File A" to "lb")
        val tableB = validSourceTable("File B" to "lb")
        val docA = documentNamed("File A")
        val refreshedA = documentNamed("Refreshed A")
        val docB = documentNamed("File B")
        val releaseRefreshA = CompletableDeferred<Unit>()
        var fileACalls = 0
        every { csvParser.parse(any()) } returnsMany listOf(
            CsvParser.ParseResult.Success(tableA), CsvParser.ParseResult.Success(tableB)
        )
        coEvery { importService.processCsv(any(), any()) } coAnswers {
            when (secondArg<List<Map<String, String>>>().single()[CsvParser.HEADER_INGREDIENT_NAME]) {
                "File A" -> if (fileACalls++ == 0) docA else {
                    releaseRefreshA.await()
                    refreshedA
                }
                else -> docB
            }
        }
        coEvery { importRepository.commitImport(any(), any()) } returns
            ImportResult.Failure(ImportFailure.StateChanged)

        viewModel.loadCsv(ByteArrayInputStream(byteArrayOf()))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.confirmImport()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.refreshPreview()
        testDispatcher.scheduler.runCurrent()

        viewModel.loadCsv(ByteArrayInputStream(byteArrayOf()))
        testDispatcher.scheduler.runCurrent()
        assertThat(viewModel.uiState.value.document).isEqualTo(docB)

        releaseRefreshA.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.uiState.value.sourceTable).isEqualTo(tableB)
        assertThat(viewModel.uiState.value.columnMapping!!.isValid).isTrue()
        assertThat(viewModel.uiState.value.document).isEqualTo(docB)
        assertThat(viewModel.uiState.value.importResult).isNull()
    }

    @Test
    fun `stale row update cannot overwrite newly loaded file`() = runTest {
        stubRestaurant()
        val tableA = validSourceTable("File A" to "lb")
        val tableB = validSourceTable("File B" to "lb")
        val docA = documentNamed("File A")
        val updatedA = documentNamed("Updated A")
        val docB = documentNamed("File B")
        val releaseUpdateA = CompletableDeferred<Unit>()
        every { csvParser.parse(any()) } returnsMany listOf(
            CsvParser.ParseResult.Success(tableA), CsvParser.ParseResult.Success(tableB)
        )
        coEvery { importService.processCsv(any(), any()) } coAnswers {
            when (secondArg<List<Map<String, String>>>().single()[CsvParser.HEADER_INGREDIENT_NAME]) {
                "Edited A" -> {
                    releaseUpdateA.await()
                    updatedA
                }
                "File A" -> docA
                else -> docB
            }
        }

        viewModel.loadCsv(ByteArrayInputStream(byteArrayOf()))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.updateRow(docA.rows.single().copy(rawData = canonicalRows("Edited A" to "lb").single()))
        testDispatcher.scheduler.runCurrent()

        viewModel.loadCsv(ByteArrayInputStream(byteArrayOf()))
        testDispatcher.scheduler.runCurrent()
        assertThat(viewModel.uiState.value.document).isEqualTo(docB)

        releaseUpdateA.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.uiState.value.sourceTable).isEqualTo(tableB)
        assertThat(viewModel.uiState.value.columnMapping!!.isValid).isTrue()
        assertThat(viewModel.uiState.value.document).isEqualTo(docB)
    }

    private fun sourceTable(headers: List<String>, vararg rows: List<String>) = CsvSourceTable(
        headers.mapIndexed { index, header -> CsvSourceColumn(index, header) }, rows.toList()
    )

    private fun validSourceTable(vararg rows: Pair<String, String> = arrayOf("Tomato" to "lb")) =
        sourceTable(listOf("Item", "UOM"), *rows.map { (name, unit) -> listOf(name, unit) }.toTypedArray())

    private fun canonicalRows(vararg rows: Pair<String, String>) = rows.map { (name, unit) -> mapOf(
        CsvParser.HEADER_INGREDIENT_NAME to name,
        CsvParser.HEADER_BASE_UNIT to unit
    ) }

    private fun documentNamed(name: String) = CsvIngredientImportDocument(listOf(
        CsvIngredientImportRow(2, mapOf(CsvParser.HEADER_INGREDIENT_NAME to name), null, emptyList(), CsvImportRowStatus.READY, true)
    ))

    private fun stubRestaurant() {
        val restaurant = mockk<com.venkoi.cuentame.core.model.restaurant.Restaurant>()
        every { restaurant.id } returns com.venkoi.cuentame.core.common.ids.RestaurantId("rest-1")
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
    }
}
