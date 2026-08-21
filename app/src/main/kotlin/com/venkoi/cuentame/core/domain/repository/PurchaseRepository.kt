package com.venkoi.cuentame.core.domain.repository

import com.venkoi.cuentame.core.common.ids.IngredientId
import com.venkoi.cuentame.core.common.ids.IngredientUnitOptionId
import com.venkoi.cuentame.core.common.ids.InventoryAreaId
import com.venkoi.cuentame.core.common.ids.PurchaseLineId
import com.venkoi.cuentame.core.common.ids.PurchaseReceiptId
import com.venkoi.cuentame.core.common.ids.RestaurantId
import com.venkoi.cuentame.core.common.ids.SupplierId
import com.venkoi.cuentame.core.model.inventory.DocumentStatus
import com.venkoi.cuentame.core.model.purchase.SourceMutationResult
import com.venkoi.cuentame.core.model.purchase.PurchaseLine
import com.venkoi.cuentame.core.model.purchase.PurchaseReceipt
import com.venkoi.cuentame.core.model.purchase.DuplicateInvoiceCandidate
import com.venkoi.cuentame.core.model.purchase.PurchaseInvoiceLineMatch
import com.venkoi.cuentame.core.model.purchase.materialization.PurchaseInvoiceDraftProposal
import com.venkoi.cuentame.core.model.purchase.materialization.failure.PurchaseInvoiceMaterializationResult
import com.venkoi.cuentame.core.model.purchase.ocr.PurchaseInvoiceOcrPage
import com.venkoi.cuentame.core.model.purchase.ocr.PurchaseInvoiceOcrResult
import com.venkoi.cuentame.core.ocr.parser.PurchaseInvoiceParseResult
import com.venkoi.cuentame.core.ocr.parser.ParsedInvoiceLineCandidate
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant

data class CreatePurchaseDraftCommand(
    val restaurantId: RestaurantId,
    val supplierId: SupplierId?,
    val invoiceNumber: String?,
    val purchaseDate: Instant,
    val notes: String?
)

data class UpdatePurchaseDraftCommand(
    val receiptId: PurchaseReceiptId,
    val supplierId: SupplierId?,
    val invoiceNumber: String?,
    val purchaseDate: Instant,
    val notes: String?
)

data class SavePurchaseLineCommand(
    val receiptId: PurchaseReceiptId,
    val lineId: PurchaseLineId?, // null for create
    val ingredientId: IngredientId,
    val areaId: InventoryAreaId,
    val ingredientUnitOptionId: IngredientUnitOptionId,
    val quantityEntered: BigDecimal,
    val lineTotal: BigDecimal,
    val notes: String?
)

data class PurchaseFilter(
    val restaurantId: RestaurantId,
    val status: DocumentStatus? = null,
    val supplierId: SupplierId? = null,
    val query: String? = null
)

data class PurchaseSummary(
    val receipt: PurchaseReceipt,
    val supplierName: String?,
    val lineCount: Int,
    val totalAmount: BigDecimal
)

data class PurchaseDetails(
    val receipt: PurchaseReceipt,
    val supplierName: String?,
    val lines: List<PurchaseLineWithDetails>
)

data class PurchaseLineWithDetails(
    val line: PurchaseLine,
    val ingredientName: String?,
    val areaName: String?,
    val unitOptionName: String?,
    val baseUnitSymbol: String?
)

interface PurchaseRepository {
    fun observePurchases(
        filter: PurchaseFilter
    ): Flow<List<PurchaseSummary>>

    fun observePurchase(
        id: PurchaseReceiptId
    ): Flow<PurchaseDetails?>

    suspend fun getReceipt(id: PurchaseReceiptId): PurchaseReceipt?
    
    suspend fun createDraft(
        command: CreatePurchaseDraftCommand
    ): PurchaseReceiptId

    suspend fun updateDraft(
        command: UpdatePurchaseDraftCommand
    )

    suspend fun saveLine(
        command: SavePurchaseLineCommand
    ): PurchaseLineId

    suspend fun deleteLine(
        receiptId: PurchaseReceiptId,
        lineId: PurchaseLineId
    )

    suspend fun deleteDraft(
        id: PurchaseReceiptId
    )

    suspend fun post(
        id: PurchaseReceiptId
    )

    suspend fun acceptDuplicateForPosting(candidate: DuplicateInvoiceCandidate)

    suspend fun void(
        id: PurchaseReceiptId
    )

    suspend fun attachDocument(
        receiptId: PurchaseReceiptId,
        storedLocation: String,
        displayName: String
    ): SourceMutationResult

    suspend fun removeDocument(
        receiptId: PurchaseReceiptId
    ): SourceMutationResult

    fun observeOcrResult(
        receiptId: PurchaseReceiptId
    ): Flow<PurchaseInvoiceOcrResult?>

    suspend fun getOcrPages(
        resultId: String
    ): List<PurchaseInvoiceOcrPage>

    suspend fun saveOcrResult(
        result: PurchaseInvoiceOcrResult,
        pages: List<PurchaseInvoiceOcrPage>,
        expectedAttachmentPath: String,
        expectedDocumentSha256: String
    ): SourceMutationResult

    suspend fun deleteOcrResult(
        receiptId: PurchaseReceiptId
    ): SourceMutationResult

    fun observeParseResult(
        receiptId: PurchaseReceiptId
    ): Flow<PurchaseInvoiceParseResult?>

    suspend fun getParsedLines(
        parseResultId: String
    ): List<ParsedInvoiceLineCandidate>

    suspend fun saveParseResult(
        receiptId: PurchaseReceiptId,
        ocrResultId: String,
        sourceDocumentSha256: String,
        result: PurchaseInvoiceParseResult
    ): SourceMutationResult

    suspend fun deleteParseResult(
        receiptId: PurchaseReceiptId
    ): SourceMutationResult

    suspend fun updateParsedLine(
        receiptId: PurchaseReceiptId,
        lineIndex: Int,
        isIgnored: Boolean,
        correction: com.venkoi.cuentame.core.ocr.parser.ParsedInvoiceLineCorrection?
    ): SourceMutationResult

    /** Adds a reviewer-supplied missing line. The stored candidate has no OCR evidence. */
    suspend fun addManualParsedLine(
        receiptId: PurchaseReceiptId,
        correction: com.venkoi.cuentame.core.ocr.parser.ParsedInvoiceLineCorrection
    ): SourceMutationResult

    suspend fun updateParseResult(
        receiptId: PurchaseReceiptId,
        corrections: com.venkoi.cuentame.core.ocr.parser.PurchaseInvoiceCorrections
    ): SourceMutationResult

    suspend fun findReceiptsByInvoiceNumber(
        restaurantId: RestaurantId,
        invoiceNumber: String
    ): List<PurchaseReceipt>

    fun observeLineMatches(
        parseResultId: String
    ): Flow<List<PurchaseInvoiceLineMatch>>

    fun observeLineMatchesForReceipt(
        receiptId: PurchaseReceiptId
    ): Flow<List<PurchaseInvoiceLineMatch>>

    suspend fun saveLineMatchesForReceipt(
        receiptId: PurchaseReceiptId,
        expectedParseResultId: String,
        matches: List<PurchaseInvoiceLineMatch>
    ): SourceMutationResult

    suspend fun saveLineMatchForReceipt(
        receiptId: PurchaseReceiptId,
        expectedParseResultId: String,
        match: PurchaseInvoiceLineMatch
    ): SourceMutationResult

    suspend fun confirmInvoiceLineMatch(
        receiptId: PurchaseReceiptId,
        expectedParseResultId: String,
        expectedSupplierId: SupplierId?,
        lineIndex: Int,
        ingredientId: IngredientId,
        unitOptionId: IngredientUnitOptionId?,
        inventoryAreaId: InventoryAreaId?,
        forceLearnMapping: Boolean
    ): LearnMappingResult

    suspend fun applyInvoiceToDraft(proposal: PurchaseInvoiceDraftProposal): PurchaseInvoiceMaterializationResult
}
