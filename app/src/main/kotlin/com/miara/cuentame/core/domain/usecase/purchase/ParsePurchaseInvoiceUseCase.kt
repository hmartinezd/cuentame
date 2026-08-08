package com.miara.cuentame.core.domain.usecase.purchase

import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.ocr.api.PurchaseInvoiceOcrFailure
import com.miara.cuentame.core.ocr.parser.PurchaseInvoiceParser
import com.miara.cuentame.core.ocr.parser.PurchaseInvoiceParseResult
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ParsePurchaseInvoiceUseCase @Inject constructor(
    private val repository: PurchaseRepository,
    private val parser: PurchaseInvoiceParser
) {

    suspend fun execute(receiptId: PurchaseReceiptId): Result<PurchaseInvoiceParseResult> {
        val ocrResult = repository.observeOcrResult(receiptId).first()
            ?: return Result.failure(Exception("OCR Result missing"))

        val pages = repository.getOcrPages(ocrResult.id)
        if (pages.isEmpty()) {
            return Result.failure(Exception("No pages in OCR result"))
        }

        return try {
            val parseResult = parser.parse(pages.map { it.evidence })
            
            repository.saveParseResult(
                receiptId = receiptId,
                ocrResultId = ocrResult.id,
                sourceDocumentSha256 = ocrResult.sourceDocumentSha256,
                result = parseResult
            )
            
            Result.success(parseResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
