package com.miara.cuentame.feature.purchases.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.common.text.normalizeName
import com.miara.cuentame.core.database.repository.ActiveRestaurantProvider
import com.miara.cuentame.core.domain.repository.*
import com.miara.cuentame.core.model.ingredient.Ingredient
import com.miara.cuentame.core.model.ingredient.IngredientUnitOption
import com.miara.cuentame.core.model.inventory.InventoryArea
import com.miara.cuentame.core.model.purchase.SourceMutationResult
import com.miara.cuentame.core.model.purchase.InvoiceLineMatchStatus
import com.miara.cuentame.core.model.purchase.PurchaseInvoiceLineMatch
import com.miara.cuentame.core.model.supplier.Supplier
import com.miara.cuentame.core.model.supplier.SupplierItemMappingKeyType
import com.miara.cuentame.core.ocr.parser.PurchaseInvoiceParseResult
import com.miara.cuentame.core.ocr.parser.ParsedInvoiceLineCandidate
import com.miara.cuentame.core.ocr.parser.effectiveValue
import com.miara.cuentame.core.ocr.parser.isEdited
import com.miara.cuentame.core.ocr.parser.matching.*
import com.miara.cuentame.core.model.purchase.materialization.PurchaseInvoiceDraftProposal
import com.miara.cuentame.core.domain.usecase.purchase.GenerateInvoiceProposalUseCase
import com.miara.cuentame.core.domain.usecase.purchase.ApplyInvoiceToPurchaseDraftUseCase
import com.miara.cuentame.core.model.purchase.materialization.failure.PurchaseInvoiceMaterializationResult
import com.miara.cuentame.core.model.purchase.materialization.failure.PurchaseInvoiceMaterializationFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject

data class ReviewDetectedInvoiceUiState(
    val isLoading: Boolean = true,
    val purchaseDetails: PurchaseDetails? = null,
    val result: PurchaseInvoiceParseResult? = null,
    val matches: List<PurchaseInvoiceLineMatch> = emptyList(),
    val suggestedSuppliers: List<Supplier> = emptyList(),
    val allSuppliers: List<Supplier> = emptyList(),
    val isSaving: Boolean = false,
    val activeMappingConflict: MappingConflict? = null,
    val allIngredients: List<Ingredient> = emptyList(),
    val allAreas: List<InventoryArea> = emptyList(),
    val ingredientUnitOptions: Map<IngredientId, List<IngredientUnitOption>> = emptyMap(),
    val matchSummary: MatchSummary = MatchSummary(0, 0, 0),
    val matchingLineIndex: Int? = null,
    val preselectedIngredientId: IngredientId? = null,
    val proposal: PurchaseInvoiceDraftProposal? = null,
    val materializationFailure: PurchaseInvoiceMaterializationFailure? = null,
    val isMaterializing: Boolean = false,
    val isMaterialized: Boolean = false,
    val isConfirmingMatch: Boolean = false,
    val confirmMatchError: MatchConfirmationError? = null,
    val currencyCode: String = "USD"
)

enum class MatchConfirmationError {
    SourceChanged,
    SourceLocked,
    InvalidSelection,
    Generic
}

data class MatchSummary(
    val matched: Int,
    val review: Int,
    val unmatched: Int
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReviewDetectedInvoiceViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: PurchaseRepository,
    private val supplierRepository: SupplierRepository,
    private val mappingRepository: SupplierItemMappingRepository,
    private val ingredientRepository: IngredientRepository,
    private val areaRepository: InventoryAreaRepository,
    private val activeRestaurantProvider: ActiveRestaurantProvider,
    private val idGenerator: com.miara.cuentame.core.common.ids.IdGenerator,
    private val generateProposalUseCase: GenerateInvoiceProposalUseCase,
    private val applyInvoiceUseCase: ApplyInvoiceToPurchaseDraftUseCase
) : ViewModel() {

    private val receiptId: PurchaseReceiptId = PurchaseReceiptId(
        checkNotNull(savedStateHandle.get<String>("receiptId"))
    )

    private val _uiState = MutableStateFlow(ReviewDetectedInvoiceUiState())
    val uiState: StateFlow<ReviewDetectedInvoiceUiState> = _uiState.asStateFlow()

    private var lastAutoMatchedKey: String? = null
    private val autoMatchMutex = Mutex()
    private val confirmMatchMutex = Mutex()

    private val _proposal = MutableStateFlow<PurchaseInvoiceDraftProposal?>(null)
    val proposal: StateFlow<PurchaseInvoiceDraftProposal?> = _proposal.asStateFlow()

    init {
        observeData()
        observeProposal()
        observeCreatedIngredient()
    }

    private fun observeProposal() {
        combine(
            repository.observeParseResult(receiptId),
            repository.observeLineMatchesForReceipt(receiptId),
            repository.observePurchase(receiptId)
        ) { parseResult, matches, _ ->
            parseResult to matches
        }.flatMapLatest { (parseResult, matches) ->
            if (parseResult != null && matches.isNotEmpty()) {
                flow<PurchaseInvoiceDraftProposal?> {
                    emit(generateProposalUseCase.execute(receiptId))
                }
            } else {
                flowOf<PurchaseInvoiceDraftProposal?>(null)
            }
        }.onEach { newProposal ->
            _proposal.value = newProposal
            _uiState.update { it.copy(proposal = newProposal) }
        }.launchIn(viewModelScope)
    }

    private fun observeCreatedIngredient() {
        viewModelScope.launch {
            savedStateHandle.getStateFlow<String?>("createdIngredientId", null)
                .filterNotNull()
                .collect { idStr ->
                    val ingredientId = IngredientId(idStr)
                    val lineIndex = savedStateHandle.get<Int>("matchingLineIndex")
                    if (lineIndex != null) {
                        onSelectIngredientForMatch(ingredientId)
                        _uiState.update { 
                            it.copy(
                                matchingLineIndex = lineIndex,
                                preselectedIngredientId = ingredientId
                            ) 
                        }
                    }
                    savedStateHandle["createdIngredientId"] = null
                }
        }
    }

    fun onStartMatch(lineIndex: Int?) {
        if (lineIndex == null) {
            clearMatchingContext()
        } else {
            _uiState.update { it.copy(
                matchingLineIndex = lineIndex, 
                preselectedIngredientId = null,
                confirmMatchError = null,
                isConfirmingMatch = false
            ) }
            savedStateHandle["matchingLineIndex"] = lineIndex
        }
    }

    private fun clearMatchingContext() {
        _uiState.update { it.copy(matchingLineIndex = null, preselectedIngredientId = null) }
        savedStateHandle["matchingLineIndex"] = null
    }

    fun onStartCreateIngredient(lineIndex: Int) {
        savedStateHandle["matchingLineIndex"] = lineIndex
    }

    private fun observeData() {
        viewModelScope.launch {
            val parseResultFlow = repository.observeParseResult(receiptId)
            val matchesFlow = parseResultFlow.flatMapLatest { result ->
                if (result != null) {
                    repository.observeLineMatchesForReceipt(receiptId)
                } else {
                    flowOf(emptyList())
                }
            }

            combine(
                repository.observePurchase(receiptId),
                parseResultFlow,
                matchesFlow,
                activeRestaurantProvider.observeActiveRestaurant(),
                activeRestaurantProvider.observeActiveRestaurant().flatMapLatest { rest ->
                    if (rest != null) ingredientRepository.observeIngredients(RestaurantId(rest.id), false)
                    else flowOf(emptyList())
                },
                activeRestaurantProvider.observeActiveRestaurant().flatMapLatest { rest ->
                    if (rest != null) areaRepository.observeActiveAreas()
                    else flowOf(emptyList())
                },
                activeRestaurantProvider.observeActiveRestaurant().flatMapLatest { rest ->
                    if (rest != null) supplierRepository.observeSuppliers(RestaurantId(rest.id), false)
                    else flowOf(emptyList())
                }
            ) { array ->
                val details = array[0] as PurchaseDetails?
                val result = array[1] as PurchaseInvoiceParseResult?
                val matches = array[2] as List<PurchaseInvoiceLineMatch>
                val activeRestaurant = array[3] as com.miara.cuentame.core.database.entity.RestaurantEntity?
                val ingredients = array[4] as List<Ingredient>
                val areas = array[5] as List<InventoryArea>
                val suppliers = array[6] as List<Supplier>

                val suggestedSuppliers = if (details?.receipt?.supplierId == null && result?.supplierNameCandidate?.normalizedValue != null && activeRestaurant != null) {
                    resolveSupplierCandidates(result.supplierNameCandidate.normalizedValue!!, suppliers)
                } else emptyList()

                val currentAutoMatchKey = "${receiptId.value}_${details?.receipt?.supplierId?.value}_${result?.id}"
                if (details?.receipt?.supplierId != null && result != null && matches.isEmpty() && lastAutoMatchedKey != currentAutoMatchKey) {
                    viewModelScope.launch {
                        autoMatchMutex.withLock {
                            if (lastAutoMatchedKey != currentAutoMatchKey) {
                                try {
                                    triggerAutoMatching(details.receipt.supplierId)
                                    lastAutoMatchedKey = currentAutoMatchKey
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    // Retry safe: keep lastAutoMatchedKey as is
                                }
                            }
                        }
                    }
                }

                val activeLines = result?.lines?.filter { !it.isIgnored } ?: emptyList()
                val activeIndices = activeLines.map { it.index }.toSet()
                val activeMatches = matches.filter { it.lineIndex in activeIndices }
                
                val matched = activeMatches.count { it.status == InvoiceLineMatchStatus.CONFIRMED }
                val review = activeMatches.count { it.status == InvoiceLineMatchStatus.SUGGESTED || it.status == InvoiceLineMatchStatus.NEEDS_REVIEW }
                val unmatched = activeLines.size - matched - review

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        purchaseDetails = details,
                        result = result,
                        matches = matches,
                        suggestedSuppliers = suggestedSuppliers,
                        allSuppliers = suppliers,
                        allIngredients = ingredients,
                        allAreas = areas,
                        matchSummary = MatchSummary(matched, review, unmatched),
                        currencyCode = activeRestaurant?.currencyCode ?: "USD"
                    )
                }
            }.collect()
        }
    }

    fun onSelectSupplier(supplierId: SupplierId) {
        viewModelScope.launch {
            val details = uiState.value.purchaseDetails ?: return@launch
            repository.updateDraft(
                UpdatePurchaseDraftCommand(
                    receiptId = receiptId,
                    supplierId = supplierId,
                    invoiceNumber = details.receipt.invoiceNumber,
                    purchaseDate = details.receipt.purchaseDate,
                    notes = details.receipt.notes
                )
            )
            triggerAutoMatching(supplierId)
        }
    }

    private suspend fun triggerAutoMatching(supplierId: SupplierId?) {
        val result = uiState.value.result ?: return
        val restId = RestaurantId(activeRestaurantProvider.getActiveRestaurant().id)
        val existingMatches = uiState.value.matches

        val ingredients = ingredientRepository.getIngredients(restId, false)
        val mappings = if (supplierId != null) {
            mappingRepository.getMappingsForSupplier(restId, supplierId)
        } else emptyList()

        val catalog = InventoryCatalog(
            ingredients = ingredients.map { ing ->
                IngredientMatchModel(
                    id = ing.id.value,
                    name = ing.name,
                    normalizedName = ing.normalizedName,
                    defaultAreaId = ing.defaultAreaId?.value,
                    unitOptions = ingredientRepository.getUnitOptions(ing.id).map { opt ->
                        UnitOptionMatchModel(opt.id.value, opt.displayName, opt.displayName.normalizeName())
                    }
                )
            },
            supplierMappings = mappings.map { m ->
                SupplierItemMappingMatchModel(
                    id = m.id,
                    supplierId = m.supplierId.value,
                    keyType = m.keyType.name,
                    normalizedKey = m.normalizedKey,
                    ingredientId = m.ingredientId.value,
                    unitOptionId = m.unitOptionId?.value,
                    inventoryAreaId = m.inventoryAreaId?.value
                )
            }
        )

        val newMatches = result.lines.map { line ->
            if (line.isIgnored) return@map null
            
            val existing = existingMatches.find { it.lineIndex == line.index }
            if (existing?.status == InvoiceLineMatchStatus.CONFIRMED) {
                // Supplier A -> Supplier B invalidation rule
                val oldSupplierId = existing.supplierId
                if (oldSupplierId != null && supplierId != null && oldSupplierId != supplierId) {
                    return@map existing.copy(
                        status = InvoiceLineMatchStatus.NEEDS_REVIEW,
                        supplierId = supplierId,
                        confirmedAt = null,
                        mappingId = null
                    )
                }
                return@map existing.copy(supplierId = supplierId)
            }

            val match = PurchaseInvoiceInventoryMatcher.match(
                line = line.toEffective(),
                supplierId = supplierId?.value,
                catalog = catalog
            )
            
            val best = match.knownMapping ?: match.candidates.firstOrNull()
            
            PurchaseInvoiceLineMatch(
                parseResultId = result.id, 
                lineIndex = line.index,
                status = if (best != null) InvoiceLineMatchStatus.SUGGESTED else InvoiceLineMatchStatus.UNMATCHED,
                supplierId = supplierId,
                ingredientId = best?.ingredientId?.let { IngredientId(it) },
                unitOptionId = best?.unitOptionId?.let { IngredientUnitOptionId(it) },
                inventoryAreaId = best?.inventoryAreaId?.let { InventoryAreaId(it) },
                mappingId = best?.mappingId,
                matchMethod = best?.reason?.name,
                matchConfidence = best?.confidence ?: 0f,
                confirmedAt = null
            )
        }.filterNotNull()

        repository.saveLineMatchesForReceipt(receiptId, result.id, newMatches)
    }

    fun onConfirmMatch(
        lineIndex: Int,
        ingredientId: IngredientId,
        unitOptionId: IngredientUnitOptionId?,
        inventoryAreaId: InventoryAreaId?
    ) {
        if (!confirmMatchMutex.tryLock()) return

        viewModelScope.launch {
            try {
                val result = uiState.value.result ?: return@launch
                _uiState.update { it.copy(isConfirmingMatch = true, confirmMatchError = null) }

                val learnResult = repository.confirmInvoiceLineMatch(
                    receiptId = receiptId,
                    expectedParseResultId = result.id,
                    expectedSupplierId = uiState.value.purchaseDetails?.receipt?.supplierId,
                    lineIndex = lineIndex,
                    ingredientId = ingredientId,
                    unitOptionId = unitOptionId,
                    inventoryAreaId = inventoryAreaId,
                    forceLearnMapping = false
                )

                if (learnResult is LearnMappingResult.Conflict) {
                    _uiState.update { it.copy(activeMappingConflict = learnResult.conflict) }
                    clearMatchingContext()
                } else {
                    clearMatchingContext()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = when (e) {
                    is com.miara.cuentame.core.domain.validation.ValidationError -> when (e) {
                        com.miara.cuentame.core.domain.validation.ValidationError.ParseResultChanged -> MatchConfirmationError.SourceChanged
                        com.miara.cuentame.core.domain.validation.ValidationError.InvoiceSourceLocked -> MatchConfirmationError.SourceLocked
                        com.miara.cuentame.core.domain.validation.ValidationError.InvalidMatchStatus,
                        com.miara.cuentame.core.domain.validation.ValidationError.InvalidPurchaseUnitOption,
                        com.miara.cuentame.core.domain.validation.ValidationError.InvalidPurchaseArea -> MatchConfirmationError.InvalidSelection
                        else -> MatchConfirmationError.Generic
                    }
                    else -> MatchConfirmationError.Generic
                }
                _uiState.update { it.copy(confirmMatchError = error) }
            } finally {
                _uiState.update { it.copy(isConfirmingMatch = false) }
                confirmMatchMutex.unlock()
            }
        }
    }

    fun onConfirmConflict(replace: Boolean) {
        val conflict = uiState.value.activeMappingConflict ?: return
        val currentSupplierId = uiState.value.purchaseDetails?.receipt?.supplierId
        _uiState.update { it.copy(activeMappingConflict = null) }
        
        if (!replace) return
        
        // Revalidate supplier context
        if (currentSupplierId != conflict.existingMapping.supplierId) return

        viewModelScope.launch {
            val restId = RestaurantId(activeRestaurantProvider.getActiveRestaurant().id)
            mappingRepository.learnMapping(
                restaurantId = restId,
                supplierId = conflict.existingMapping.supplierId,
                keyType = conflict.existingMapping.keyType,
                normalizedKey = conflict.existingMapping.normalizedKey,
                sourceVendorCode = conflict.existingMapping.sourceVendorCode,
                sourceDescription = conflict.existingMapping.sourceDescription,
                sourcePackageText = conflict.existingMapping.sourcePackageText,
                ingredientId = conflict.newIngredientId,
                unitOptionId = conflict.newUnitOptionId,
                inventoryAreaId = conflict.newInventoryAreaId,
                force = true
            )
        }
    }

    fun onSelectIngredientForMatch(ingredientId: IngredientId) {
        viewModelScope.launch {
            if (uiState.value.ingredientUnitOptions.containsKey(ingredientId)) return@launch
            val options = ingredientRepository.getUnitOptions(ingredientId)
            _uiState.update { 
                it.copy(ingredientUnitOptions = it.ingredientUnitOptions + (ingredientId to options))
            }
        }
    }

    private fun ParsedInvoiceLineCandidate.toEffective(): EffectiveParsedInvoiceLine {
        return EffectiveParsedInvoiceLine(
            vendorCode = this.vendorCode.effectiveValue(this.correction?.vendorCode),
            description = this.description.effectiveValue(this.correction?.description),
            packageText = this.packageText.effectiveValue(this.correction?.packageText),
            quantity = this.quantity.effectiveValue(this.correction?.quantity),
            unitPrice = this.unitPrice.effectiveValue(this.correction?.unitPrice),
            lineTotal = this.lineTotal.effectiveValue(this.correction?.lineTotal)
        )
    }

    fun onUpdateHeaderCorrections(corrections: com.miara.cuentame.core.ocr.parser.PurchaseInvoiceCorrections) {
        viewModelScope.launch {
            val result = repository.updateParseResult(receiptId, corrections)
            if (result == SourceMutationResult.SourceLocked) {
                _uiState.update { it.copy(materializationFailure = PurchaseInvoiceMaterializationFailure.InvoiceSourceLocked) }
            }
        }
    }

    fun onUpdateLineCorrection(lineIndex: Int, isIgnored: Boolean, correction: com.miara.cuentame.core.ocr.parser.ParsedInvoiceLineCorrection?) {
        viewModelScope.launch {
            val result = uiState.value.result ?: return@launch
            val oldLine = result.lines.find { it.index == lineIndex } ?: return@launch
            
            val identityChanged = identityFieldsChanged(oldLine, correction)
            
            val status = repository.updateParsedLine(receiptId, lineIndex, isIgnored, correction)
            if (status == SourceMutationResult.SourceLocked) {
                _uiState.update { it.copy(materializationFailure = PurchaseInvoiceMaterializationFailure.InvoiceSourceLocked) }
                return@launch
            }
            
            if (isIgnored || identityChanged) {
                repository.saveLineMatchForReceipt(receiptId, result.id, unmatchedMatch(result.id, lineIndex))
            }
            
            if (!isIgnored && identityChanged) {
                triggerAutoMatchingForLine(lineIndex, correction)
            }
        }
    }

    private fun identityFieldsChanged(oldLine: ParsedInvoiceLineCandidate, newCorrection: com.miara.cuentame.core.ocr.parser.ParsedInvoiceLineCorrection?): Boolean {
        val oldIdentity = InvoiceLineMatchingIdentity.from(
            oldLine.vendorCode.effectiveValue(oldLine.correction?.vendorCode),
            oldLine.description.effectiveValue(oldLine.correction?.description),
            oldLine.packageText.effectiveValue(oldLine.correction?.packageText)
        )
        val newLine = oldLine.copy(correction = newCorrection)
        val newIdentity = InvoiceLineMatchingIdentity.from(
            newLine.vendorCode.effectiveValue(newLine.correction?.vendorCode),
            newLine.description.effectiveValue(newLine.correction?.description),
            newLine.packageText.effectiveValue(newLine.correction?.packageText)
        )
        return oldIdentity != newIdentity
    }

    private fun unmatchedMatch(parseResultId: String, lineIndex: Int) = PurchaseInvoiceLineMatch(
        parseResultId = parseResultId,
        lineIndex = lineIndex,
        status = InvoiceLineMatchStatus.UNMATCHED,
        supplierId = uiState.value.purchaseDetails?.receipt?.supplierId,
        ingredientId = null,
        unitOptionId = null,
        inventoryAreaId = null,
        mappingId = null,
        matchMethod = null,
        matchConfidence = 0f,
        confirmedAt = null
    )

    private suspend fun triggerAutoMatchingForLine(lineIndex: Int, correction: com.miara.cuentame.core.ocr.parser.ParsedInvoiceLineCorrection?) {
        val result = uiState.value.result ?: return
        val line = result.lines.find { it.index == lineIndex } ?: return
        val supplierId = uiState.value.purchaseDetails?.receipt?.supplierId
        val restId = RestaurantId(activeRestaurantProvider.getActiveRestaurant().id)

        val ingredients = ingredientRepository.getIngredients(restId, false)
        val mappings = if (supplierId != null) {
            mappingRepository.getMappingsForSupplier(restId, supplierId)
        } else emptyList()

        val catalog = InventoryCatalog(
            ingredients = ingredients.map { ing ->
                IngredientMatchModel(
                    id = ing.id.value,
                    name = ing.name,
                    normalizedName = ing.normalizedName,
                    defaultAreaId = ing.defaultAreaId?.value,
                    unitOptions = ingredientRepository.getUnitOptions(ing.id).map { opt ->
                        UnitOptionMatchModel(opt.id.value, opt.displayName, opt.displayName.normalizeName())
                    }
                )
            },
            supplierMappings = mappings.map { m ->
                SupplierItemMappingMatchModel(
                    id = m.id,
                    supplierId = m.supplierId.value,
                    keyType = m.keyType.name,
                    normalizedKey = m.normalizedKey,
                    ingredientId = m.ingredientId.value,
                    unitOptionId = m.unitOptionId?.value,
                    inventoryAreaId = m.inventoryAreaId?.value
                )
            }
        )

        val match = PurchaseInvoiceInventoryMatcher.match(
            line = line.copy(correction = correction).toEffective(),
            supplierId = supplierId?.value,
            catalog = catalog
        )
        
        val best = match.knownMapping ?: match.candidates.firstOrNull()
        
        val newMatch = PurchaseInvoiceLineMatch(
            parseResultId = result.id,
            lineIndex = lineIndex,
            status = if (best != null) InvoiceLineMatchStatus.SUGGESTED else InvoiceLineMatchStatus.UNMATCHED,
            supplierId = supplierId,
            ingredientId = best?.ingredientId?.let { IngredientId(it) },
            unitOptionId = best?.unitOptionId?.let { IngredientUnitOptionId(it) },
            inventoryAreaId = best?.inventoryAreaId?.let { InventoryAreaId(it) },
            mappingId = best?.mappingId,
            matchMethod = best?.reason?.name,
            matchConfidence = best?.confidence ?: 0f,
            confirmedAt = null
        )
        repository.saveLineMatchForReceipt(receiptId, result.id, newMatch)
    }

    fun onToggleIgnoreLine(lineIndex: Int) {
        viewModelScope.launch {
            val result = uiState.value.result ?: return@launch
            val line = result.lines.find { it.index == lineIndex } ?: return@launch
            val isIgnored = !line.isIgnored
            val status = repository.updateParsedLine(receiptId, lineIndex, isIgnored, line.correction)
            if (status == SourceMutationResult.SourceLocked) {
                _uiState.update { it.copy(materializationFailure = PurchaseInvoiceMaterializationFailure.InvoiceSourceLocked) }
                return@launch
            }
            if (isIgnored) {
                repository.saveLineMatchForReceipt(receiptId, result.id, unmatchedMatch(result.id, lineIndex))
            } else {
                triggerAutoMatchingForLine(lineIndex, line.correction)
            }
        }
    }

    fun onAddMissingLine(correction: com.miara.cuentame.core.ocr.parser.ParsedInvoiceLineCorrection) {
        viewModelScope.launch {
            val status = repository.addManualParsedLine(receiptId, correction)
            if (status == SourceMutationResult.SourceLocked) {
                _uiState.update { it.copy(materializationFailure = PurchaseInvoiceMaterializationFailure.InvoiceSourceLocked) }
            }
        }
    }
    
    fun onResetHeader() {
        viewModelScope.launch {
            val result = repository.updateParseResult(receiptId, com.miara.cuentame.core.ocr.parser.PurchaseInvoiceCorrections())
            if (result == SourceMutationResult.SourceLocked) {
                _uiState.update { it.copy(materializationFailure = PurchaseInvoiceMaterializationFailure.InvoiceSourceLocked) }
            }
        }
    }
    
    fun onResetLine(lineIndex: Int) {
        viewModelScope.launch {
            val result = uiState.value.result ?: return@launch
            val line = result.lines.find { it.index == lineIndex } ?: return@launch
            
            val identityChanged = identityFieldsChanged(line, null)
            
            val status = repository.updateParsedLine(receiptId, lineIndex, line.isIgnored, null)
            if (status == SourceMutationResult.SourceLocked) {
                _uiState.update { it.copy(materializationFailure = PurchaseInvoiceMaterializationFailure.InvoiceSourceLocked) }
                return@launch
            }
            
            if (identityChanged) {
                repository.saveLineMatchForReceipt(receiptId, result.id, unmatchedMatch(result.id, lineIndex))
                triggerAutoMatchingForLine(lineIndex, null)
            }
        }
    }

    fun onCreateQuickIngredient(name: String, onCreated: (IngredientId) -> Unit) {
        // Redirection handled via UI callback to NavController
    }

    fun onApplyToDraft() {
        val proposal = uiState.value.proposal ?: return
        if (proposal.blockingIssues.isNotEmpty()) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isMaterializing = true, materializationFailure = null) }
            when (val result = applyInvoiceUseCase.execute(proposal)) {
                is PurchaseInvoiceMaterializationResult.Success -> {
                    _uiState.update { it.copy(isMaterializing = false, isMaterialized = true) }
                }
                is PurchaseInvoiceMaterializationResult.Failure -> {
                    _uiState.update { it.copy(isMaterializing = false, materializationFailure = result.reason) }
                }
            }
        }
    }

    fun onContinueDuplicate() {
        val failure = uiState.value.materializationFailure as?
            PurchaseInvoiceMaterializationFailure.StrongDuplicate ?: return
        val proposal = uiState.value.proposal ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isMaterializing = true, materializationFailure = null) }
            when (val result = applyInvoiceUseCase.execute(proposal.copy(acceptedDuplicate = failure.candidate))) {
                PurchaseInvoiceMaterializationResult.Success ->
                    _uiState.update { it.copy(isMaterializing = false, isMaterialized = true) }
                is PurchaseInvoiceMaterializationResult.Failure ->
                    _uiState.update { it.copy(isMaterializing = false, materializationFailure = result.reason) }
            }
        }
    }

    fun clearMaterializationFailure() {
        _uiState.update { it.copy(materializationFailure = null) }
    }

    companion object {
        /** Matching-only normalization. It never changes canonical supplier or raw OCR text. */
        fun resolveSupplierCandidates(detectedName: String, suppliers: List<Supplier>): List<Supplier> {
            val primary = detectedName.normalizeName()
            val compact = primary.filter { it.isLetterOrDigit() }
            return suppliers.filter { supplier ->
                val canonical = supplier.name.normalizeName()
                canonical == primary ||
                    (compact.length >= 6 && canonical.filter { it.isLetterOrDigit() } == compact)
            }
        }

        fun isMatchSelectionValid(
            ingredientId: IngredientId?,
            unitOptionId: IngredientUnitOptionId?,
            areaId: InventoryAreaId?,
            ingredientUnitOptions: List<IngredientUnitOption>?
        ): Boolean {
            return ingredientId != null &&
                    unitOptionId != null &&
                    areaId != null &&
                    ingredientUnitOptions?.any { it.id == unitOptionId } == true
        }
    }
}
