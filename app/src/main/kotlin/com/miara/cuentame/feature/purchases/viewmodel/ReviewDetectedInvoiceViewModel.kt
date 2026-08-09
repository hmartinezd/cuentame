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
import com.miara.cuentame.core.model.purchase.InvoiceLineMatchStatus
import com.miara.cuentame.core.model.purchase.PurchaseInvoiceLineMatch
import com.miara.cuentame.core.model.supplier.Supplier
import com.miara.cuentame.core.model.supplier.SupplierItemMappingKeyType
import com.miara.cuentame.core.ocr.parser.PurchaseInvoiceParseResult
import com.miara.cuentame.core.ocr.parser.ParsedInvoiceLineCandidate
import com.miara.cuentame.core.ocr.parser.effectiveValue
import com.miara.cuentame.core.ocr.parser.matching.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    val matchSummary: MatchSummary = MatchSummary(0, 0, 0)
)

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
    private val idGenerator: com.miara.cuentame.core.common.ids.IdGenerator
) : ViewModel() {

    private val receiptId: PurchaseReceiptId = PurchaseReceiptId(
        checkNotNull(savedStateHandle.get<String>("receiptId"))
    )

    private val _uiState = MutableStateFlow(ReviewDetectedInvoiceUiState())
    val uiState: StateFlow<ReviewDetectedInvoiceUiState> = _uiState.asStateFlow()

    private var lastAutoMatchedKey: String? = null

    init {
        observeData()
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
                    supplierRepository.searchSuppliers(RestaurantId(activeRestaurant.id), result.supplierNameCandidate.normalizedValue!!)
                } else emptyList()

                val currentAutoMatchKey = "${receiptId.value}_${details?.receipt?.supplierId?.value}"
                if (details?.receipt?.supplierId != null && result != null && matches.isEmpty() && lastAutoMatchedKey != currentAutoMatchKey) {
                    lastAutoMatchedKey = currentAutoMatchKey
                    triggerAutoMatching(details.receipt.supplierId)
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
                        matchSummary = MatchSummary(matched, review, unmatched)
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
                return@map existing.copy(supplierId = supplierId)
            }

            val match = PurchaseInvoiceInventoryMatcher.match(
                line = line.toEffective(),
                supplierId = supplierId?.value,
                catalog = catalog
            )
            
            val best = match.knownMapping ?: match.candidates.firstOrNull()
            
            PurchaseInvoiceLineMatch(
                parseResultId = "", 
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

        repository.saveLineMatchesForReceipt(receiptId, newMatches)
    }

    fun onConfirmMatch(
        lineIndex: Int,
        ingredientId: IngredientId,
        unitOptionId: IngredientUnitOptionId?,
        inventoryAreaId: InventoryAreaId?
    ) {
        viewModelScope.launch {
            val result = uiState.value.result ?: return@launch
            val line = result.lines.find { it.index == lineIndex } ?: return@launch
            val supplierId = uiState.value.purchaseDetails?.receipt?.supplierId
            val restId = RestaurantId(activeRestaurantProvider.getActiveRestaurant().id)

            val match = PurchaseInvoiceLineMatch(
                parseResultId = "",
                lineIndex = lineIndex,
                status = InvoiceLineMatchStatus.CONFIRMED,
                supplierId = supplierId,
                ingredientId = ingredientId,
                unitOptionId = unitOptionId,
                inventoryAreaId = inventoryAreaId,
                mappingId = null,
                matchMethod = "UserSelection",
                matchConfidence = 1.0f,
                confirmedAt = Instant.now()
            )
            repository.saveLineMatchForReceipt(receiptId, match)

            if (supplierId != null) {
                val effectiveLine = line.toEffective()
                val normalizedCode = InventoryNormalization.normalizeVendorCode(effectiveLine.vendorCode)
                
                val mappingParams = if (normalizedCode.isNotEmpty()) {
                    SupplierItemMappingKeyType.VENDOR_CODE to normalizedCode
                } else {
                    val d = InventoryNormalization.normalizeDescription(effectiveLine.description)
                    val p = InventoryNormalization.normalizePackageText(effectiveLine.packageText)
                    SupplierItemMappingKeyType.DESCRIPTION_PACKAGE to "$d|$p"
                }
                val keyType = mappingParams.first
                val key = mappingParams.second

                val learnResult = mappingRepository.learnMapping(
                    restaurantId = restId,
                    supplierId = supplierId,
                    keyType = keyType,
                    normalizedKey = key,
                    sourceVendorCode = effectiveLine.vendorCode,
                    sourceDescription = effectiveLine.description,
                    sourcePackageText = effectiveLine.packageText,
                    ingredientId = ingredientId,
                    unitOptionId = unitOptionId,
                    inventoryAreaId = inventoryAreaId,
                    force = false
                )

                if (learnResult is LearnMappingResult.Conflict) {
                    _uiState.update { it.copy(activeMappingConflict = learnResult.conflict) }
                }
            }
        }
    }

    fun onConfirmConflict(replace: Boolean) {
        val conflict = uiState.value.activeMappingConflict ?: return
        _uiState.update { it.copy(activeMappingConflict = null) }
        
        if (!replace) return

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
            repository.updateParseResult(receiptId, corrections)
        }
    }

    fun onUpdateLineCorrection(lineIndex: Int, isIgnored: Boolean, correction: com.miara.cuentame.core.ocr.parser.ParsedInvoiceLineCorrection?) {
        viewModelScope.launch {
            val result = uiState.value.result ?: return@launch
            val oldLine = result.lines.find { it.index == lineIndex } ?: return@launch
            
            val identityChanged = identityFieldsChanged(oldLine, correction)
            
            repository.updateParsedLine(receiptId, lineIndex, isIgnored, correction)
            
            if (isIgnored || identityChanged) {
                repository.saveLineMatchForReceipt(receiptId, unmatchedMatch(lineIndex))
            }
            
            if (!isIgnored && identityChanged) {
                triggerAutoMatchingForLine(lineIndex, correction)
            }
        }
    }

    private fun identityFieldsChanged(oldLine: ParsedInvoiceLineCandidate, newCorrection: com.miara.cuentame.core.ocr.parser.ParsedInvoiceLineCorrection?): Boolean {
        val oldEffective = oldLine.toEffective()
        val newEffective = oldLine.copy(correction = newCorrection).toEffective()
        return oldEffective.vendorCode != newEffective.vendorCode ||
               oldEffective.description != newEffective.description ||
               oldEffective.packageText != newEffective.packageText
    }

    private fun unmatchedMatch(lineIndex: Int) = PurchaseInvoiceLineMatch(
        parseResultId = "",
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
            parseResultId = "",
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
        repository.saveLineMatchForReceipt(receiptId, newMatch)
    }

    fun onToggleIgnoreLine(lineIndex: Int) {
        viewModelScope.launch {
            val line = uiState.value.result?.lines?.find { it.index == lineIndex } ?: return@launch
            val isIgnored = !line.isIgnored
            repository.updateParsedLine(receiptId, lineIndex, isIgnored, line.correction)
            if (isIgnored) {
                repository.saveLineMatchForReceipt(receiptId, unmatchedMatch(lineIndex))
            } else {
                triggerAutoMatchingForLine(lineIndex, line.correction)
            }
        }
    }
    
    fun onResetHeader() {
        viewModelScope.launch {
            repository.updateParseResult(receiptId, com.miara.cuentame.core.ocr.parser.PurchaseInvoiceCorrections())
        }
    }
    
    fun onResetLine(lineIndex: Int) {
        viewModelScope.launch {
            val line = uiState.value.result?.lines?.find { it.index == lineIndex } ?: return@launch
            repository.updateParsedLine(receiptId, lineIndex, line.isIgnored, null)
        }
    }

    fun onCreateQuickIngredient(name: String, onCreated: (IngredientId) -> Unit) {
        viewModelScope.launch {
            val restId = RestaurantId(activeRestaurantProvider.getActiveRestaurant().id)
            val ingredientId = IngredientId(idGenerator.newId())
            val baseUnitId = com.miara.cuentame.core.common.ids.UnitId("count_each")
            val now = Instant.now()
            
            val ingredient = Ingredient(
                id = ingredientId,
                restaurantId = restId,
                name = name,
                normalizedName = name.normalizeName(),
                categoryId = null,
                baseUnitId = baseUnitId,
                defaultAreaId = null,
                isActive = true,
                createdAt = now,
                updatedAt = now
            )
            
            val baseOption = IngredientUnitOption(
                id = IngredientUnitOptionId(idGenerator.newId()),
                ingredientId = ingredientId,
                displayName = "ea",
                shortLabel = "ea",
                standardUnitId = baseUnitId,
                factorToBase = java.math.BigDecimal.ONE,
                isBase = true,
                isDefaultCount = true,
                isDefaultPurchase = true,
                isActive = true,
                createdAt = now,
                updatedAt = now
            )
            
            ingredientRepository.createIngredientWithBaseOption(ingredient, baseOption)
            
            // Pre-load options for the new ingredient
            onSelectIngredientForMatch(ingredientId)
            
            onCreated(ingredientId)
        }
    }
}
