package com.miara.cuentame.feature.ingredients.csvimport.ui

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.domain.repository.CsvImportRepository
import com.miara.cuentame.core.domain.repository.ImportResult
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvIngredientImportDocument
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvImportService
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvParser
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvTemplateGenerator
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvImportRowStatus
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvIngredientImportRow
import com.miara.cuentame.core.domain.repository.ImportFailure
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
    fun `loadCsv success updates state`() = runTest {
        val restaurant = mockk<com.miara.cuentame.core.model.restaurant.Restaurant>()
        every { restaurant.id } returns com.miara.cuentame.core.common.ids.RestaurantId("rest-1")
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        
        val rows = listOf(mapOf("name" to "Tomato"))
        every { csvParser.parse(any()) } returns CsvParser.ParseResult.Success(rows)
        
        val doc = CsvIngredientImportDocument(emptyList())
        coEvery { importService.processCsv(any(), any()) } returns doc
        
        viewModel.loadCsv(ByteArrayInputStream("".toByteArray()))
        testDispatcher.scheduler.advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertThat(state.document == doc).isTrue()
        assertThat(state.isParsing).isFalse()
    }

    @Test
    fun `confirmImport single-flight guard`() = runTest {
        val restaurant = mockk<com.miara.cuentame.core.model.restaurant.Restaurant>()
        every { restaurant.id } returns com.miara.cuentame.core.common.ids.RestaurantId("rest-1")
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        
        val doc = CsvIngredientImportDocument(listOf(
            CsvIngredientImportRow(1, emptyMap(), null, emptyList(), CsvImportRowStatus.READY, true)
        ))
        
        // Use manual update to set document
        viewModel.loadCsv(ByteArrayInputStream("".toByteArray())) // trigger load
        // But we need a document in state. We'll mock the parser/service
        every { csvParser.parse(any()) } returns CsvParser.ParseResult.Success(emptyList())
        coEvery { importService.processCsv(any(), any()) } returns doc
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
        every { csvParser.parse(any()) } returns CsvParser.ParseResult.Success(emptyList())
        coEvery { restaurantRepository.getRestaurant() } returns null

        viewModel.loadCsv(ByteArrayInputStream(byteArrayOf()))
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.isParsing).isFalse()
        assertThat(viewModel.uiState.value.importResult)
            .isEqualTo(ImportResult.Failure(ImportFailure.RestaurantUnavailable))
    }

    @Test
    fun `typed parser warning reaches UI and header-only document cannot confirm`() = runTest {
        val restaurant = mockk<com.miara.cuentame.core.model.restaurant.Restaurant>()
        every { restaurant.id } returns com.miara.cuentame.core.common.ids.RestaurantId("rest-1")
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        val warning = CsvParser.CsvParserWarning.UnknownColumn("legacy")
        every { csvParser.parse(any()) } returns CsvParser.ParseResult.Success(emptyList(), listOf(warning))
        coEvery { importService.processCsv(any(), emptyList()) } returns CsvIngredientImportDocument(emptyList())

        viewModel.loadCsv(ByteArrayInputStream(byteArrayOf()))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.confirmImport()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.parserWarnings).containsExactly(warning)
        assertThat(viewModel.uiState.value.document?.rows).isEmpty()
        io.mockk.coVerify(exactly = 0) { importRepository.commitImport(any(), any()) }
    }

    @Test
    fun `skipping error enables confirmation and reincluding blocks it`() = runTest {
        val restaurant = mockk<com.miara.cuentame.core.model.restaurant.Restaurant>()
        every { restaurant.id } returns com.miara.cuentame.core.common.ids.RestaurantId("rest-1")
        coEvery { restaurantRepository.getRestaurant() } returns restaurant
        val errorRow = CsvIngredientImportRow(2, emptyMap(), null, emptyList(), CsvImportRowStatus.ERROR, true)
        val readyRow = CsvIngredientImportRow(3, emptyMap(), null, emptyList(), CsvImportRowStatus.READY, true)
        every { csvParser.parse(any()) } returns CsvParser.ParseResult.Success(emptyList())
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
}
