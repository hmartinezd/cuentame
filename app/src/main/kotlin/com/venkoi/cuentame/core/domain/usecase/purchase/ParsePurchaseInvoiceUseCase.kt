package com.venkoi.cuentame.core.domain.usecase.purchase

import com.venkoi.cuentame.core.common.ids.PurchaseReceiptId
import com.venkoi.cuentame.core.domain.repository.PurchaseRepository
import com.venkoi.cuentame.core.model.purchase.SourceMutationResult
import com.venkoi.cuentame.core.ocr.parser.PurchaseInvoiceParser
import com.venkoi.cuentame.core.ocr.parser.PurchaseInvoiceParseResult
import kotlinx.coroutines.flow.first
import javax.inject.Inject

sealed interface ParsePurchaseInvoiceResult {
    data class Success(val result: PurchaseInvoiceParseResult) : ParsePurchaseInvoiceResult
    data class Failure(val reason: PurchaseInvoiceParseFailure) : ParsePurchaseInvoiceResult
}

enum class PurchaseInvoiceParseFailure {
    NoOcrEvidence,
    InvalidOcrEvidence,
    ParserFailed,
    OcrChanged,
    PersistenceFailed,
    SourceLocked,
    Unknown
}

class ParsePurchaseInvoiceUseCase @Inject constructor(
    private val repository: PurchaseRepository,
    private val parser: PurchaseInvoiceParser
) {

    suspend fun execute(receiptId: PurchaseReceiptId): ParsePurchaseInvoiceResult {
        val ocrResult = repository.observeOcrResult(receiptId).first()
            ?: return ParsePurchaseInvoiceResult.Failure(PurchaseInvoiceParseFailure.NoOcrEvidence)

        val pages = repository.getOcrPages(ocrResult.id)
        if (pages.isEmpty()) {
            return ParsePurchaseInvoiceResult.Failure(PurchaseInvoiceParseFailure.InvalidOcrEvidence)
        }

        return try {
            val parseResult = parser.parse(pages.map { it.evidence })
            
            // Duplicate check
            val receipt = repository.getReceipt(receiptId)
            val finalResult = if (receipt != null) {
                val invoiceNumber = parseResult.invoiceNumber.normalizedValue
                if (invoiceNumber != null) {
                    val duplicates = repository.findReceiptsByInvoiceNumber(receipt.restaurantId, invoiceNumber)
                        .filter { it.id != receiptId }
                    if (duplicates.isNotEmpty()) {
                        parseResult.copy(warnings = parseResult.warnings + com.venkoi.cuentame.core.ocr.parser.InvoiceParseWarning.PossibleDuplicate)
                    } else {
                        parseResult
                    }
                } else {
                    parseResult
                }
            } else {
                parseResult
            }

            val saveStatus = repository.saveParseResult(
                receiptId = receiptId,
                ocrResultId = ocrResult.id,
                sourceDocumentSha256 = ocrResult.sourceDocumentSha256,
                result = finalResult
            )

            if (saveStatus == SourceMutationResult.SourceLocked) {
                return ParsePurchaseInvoiceResult.Failure(PurchaseInvoiceParseFailure.SourceLocked)
            }
            
            ParsePurchaseInvoiceResult.Success(finalResult)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val reason = when {
                e is IllegalStateException && e.message?.contains("OCR changed") == true -> PurchaseInvoiceParseFailure.OcrChanged
                e is java.sql.SQLException -> PurchaseInvoiceParseFailure.PersistenceFailed
                else -> PurchaseInvoiceParseFailure.ParserFailed
            }
            ParsePurchaseInvoiceResult.Failure(reason)
        }
    }
}
