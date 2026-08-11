package com.miara.cuentame.feature.ingredients.csvimport.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.domain.repository.CsvImportRepository
import com.miara.cuentame.core.domain.repository.ImportFailure
import com.miara.cuentame.core.domain.repository.ImportResult
import com.miara.cuentame.core.domain.repository.RestaurantRepository
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvIngredientImportDocument
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvIngredientImportRow
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvImportService
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvImportRowStatus
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvParser
import com.miara.cuentame.feature.ingredients.csvimport.domain.CsvTemplateGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

@HiltViewModel
class CsvImportViewModel @Inject constructor(
    private val csvParser: CsvParser,
    private val importService: CsvImportService,
    private val importRepository: CsvImportRepository,
    private val restaurantRepository: RestaurantRepository,
    private val templateGenerator: CsvTemplateGenerator
) : ViewModel() {

    private val _uiState = MutableStateFlow(CsvImportUiState())
    val uiState: StateFlow<CsvImportUiState> = _uiState.asStateFlow()

    private val confirmMutex = Mutex()
    private val loadGeneration = AtomicLong()

    internal var parsingDispatcher: CoroutineDispatcher = Dispatchers.IO

    fun loadCsv(inputStream: InputStream) {
        val generation = loadGeneration.incrementAndGet()
        _uiState.update {
            it.copy(
                document = null,
                isParsing = true,
                parseError = null,
                parserWarnings = emptyList(),
                importResult = null
            )
        }
        viewModelScope.launch {
            try {
                val parseResult = try {
                    withContext(parsingDispatcher) { inputStream.use(csvParser::parse) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    CsvParser.ParseResult.Error(CsvParser.ParseErrorType.READ_FAILURE)
                }
                if (generation != loadGeneration.get()) return@launch
                when (parseResult) {
                    is CsvParser.ParseResult.Success -> {
                        try {
                            val restaurant = restaurantRepository.getRestaurant()
                            if (restaurant == null) {
                                _uiState.update { it.copy(importResult = ImportResult.Failure(ImportFailure.RestaurantUnavailable)) }
                                return@launch
                            }
                            val document = importService.processCsv(restaurant.id, parseResult.rows)
                            if (generation == loadGeneration.get()) {
                                _uiState.update { it.copy(document = document, parserWarnings = parseResult.warnings) }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            if (generation == loadGeneration.get()) {
                                _uiState.update { it.copy(importResult = ImportResult.Failure(ImportFailure.Unexpected)) }
                            }
                        }
                    }
                    is CsvParser.ParseResult.Error -> _uiState.update { it.copy(parseError = parseResult.type) }
                }
            } finally {
                if (generation == loadGeneration.get()) {
                    _uiState.update { it.copy(isParsing = false) }
                }
            }
        }
    }

    fun refreshPreview() {
        val staleDocument = _uiState.value.document ?: return
        if (_uiState.value.importResult != ImportResult.Failure(ImportFailure.StateChanged)) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                val restaurant = restaurantRepository.getRestaurant() ?: return@launch
                val refreshed = importService.processCsv(restaurant.id, staleDocument.rows.map { it.rawData })
                val selections = staleDocument.rows.associate { it.rowNumber to it.isIncluded }
                val rows = refreshed.rows.map { row ->
                    row.copy(isIncluded = selections[row.rowNumber] ?: row.isIncluded)
                }
                _uiState.update { it.copy(document = refreshed.copy(rows = rows), importResult = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep StateChanged visible until a refresh succeeds.
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun toggleRowSelection(rowNumber: Int) {
        _uiState.update { state ->
            val doc = state.document ?: return@update state
            val updatedRows = doc.rows.map { row ->
                if (row.rowNumber == rowNumber) {
                    row.copy(isIncluded = !row.isIncluded)
                } else row
            }
            state.copy(document = doc.copy(rows = updatedRows))
        }
    }

    fun updateRow(updatedRow: CsvIngredientImportRow) {
        viewModelScope.launch {
            val restaurant = restaurantRepository.getRestaurant() ?: return@launch
            val doc = _uiState.value.document ?: return@launch
            
            val updatedRawRows = doc.rows.map { row ->
                if (row.rowNumber == updatedRow.rowNumber) updatedRow.rawData else row.rawData
            }
            
            val newDoc = importService.processCsv(restaurant.id, updatedRawRows)
            
            val finalRows = newDoc.rows.map { newRow ->
                val oldRow = doc.rows.find { it.rowNumber == newRow.rowNumber }
                newRow.copy(isIncluded = oldRow?.isIncluded ?: true)
            }
            
            _uiState.update { it.copy(document = newDoc.copy(rows = finalRows)) }
        }
    }

    fun confirmImport() {
        val state = _uiState.value
        val doc = state.document ?: return
        if (state.isCommitting) return
        if (doc.rows.none { it.isIncluded } || doc.rows.any { it.isIncluded && it.status == CsvImportRowStatus.ERROR }) return

        viewModelScope.launch {
            if (!confirmMutex.tryLock()) return@launch
            try {
                _uiState.update { it.copy(isCommitting = true, importResult = null) }
                val restaurant = restaurantRepository.getRestaurant()
                if (restaurant == null) {
                    _uiState.update { 
                        it.copy(
                            isCommitting = false, 
                            importResult = ImportResult.Failure(ImportFailure.RestaurantUnavailable)
                        ) 
                    }
                    return@launch
                }
                
                val result = importRepository.commitImport(restaurant.id, doc)
                _uiState.update { it.copy(isCommitting = false, importResult = result) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update { 
                    it.copy(
                        isCommitting = false, 
                        importResult = ImportResult.Failure(ImportFailure.Unexpected)
                    ) 
                }
            } finally {
                _uiState.update { it.copy(isCommitting = false) }
                confirmMutex.unlock()
            }
        }
    }

    fun resetImportResult() {
        _uiState.update { it.copy(importResult = null) }
    }

    fun generateTemplate(outputStream: OutputStream) {
        templateGenerator.generate(outputStream)
    }
}

data class CsvImportUiState(
    val document: CsvIngredientImportDocument? = null,
    val isParsing: Boolean = false,
    val parseError: CsvParser.ParseErrorType? = null,
    val parserWarnings: List<CsvParser.CsvParserWarning> = emptyList(),
    val isRefreshing: Boolean = false,
    val isCommitting: Boolean = false,
    val importResult: ImportResult? = null
)
