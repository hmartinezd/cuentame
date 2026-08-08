package com.miara.cuentame.core.domain.usecase.purchase

import com.miara.cuentame.core.backup.api.PurchaseDocumentStore
import com.miara.cuentame.core.backup.api.PurchasePdfPageRenderResult
import com.miara.cuentame.core.backup.api.PurchasePdfRenderFailure
import com.miara.cuentame.core.backup.api.PurchasePdfRenderer
import com.miara.cuentame.core.common.hash.HashUtils
import com.miara.cuentame.core.common.image.SafeImageDecoder
import com.miara.cuentame.core.common.ids.IdGenerator
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.time.TimeProvider
import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.ocr.api.OcrPageEvidence
import com.miara.cuentame.core.ocr.api.PurchaseInvoiceOcrEngine
import com.miara.cuentame.core.ocr.api.PurchaseInvoiceOcrFailure
import com.miara.cuentame.core.model.purchase.ocr.PurchaseInvoiceOcrPage
import com.miara.cuentame.core.model.purchase.ocr.PurchaseInvoiceOcrResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

sealed interface AnalyzePurchaseInvoiceResult {
    data object Preparing : AnalyzePurchaseInvoiceResult
    data class ProcessingPage(val current: Int, val total: Int) : AnalyzePurchaseInvoiceResult
    data object Saving : AnalyzePurchaseInvoiceResult
    data object Parsing : AnalyzePurchaseInvoiceResult
    data object Success : AnalyzePurchaseInvoiceResult
    data class Failure(val reason: PurchaseInvoiceOcrFailure) : AnalyzePurchaseInvoiceResult
    data class ParseFailed(val ocrResult: PurchaseInvoiceOcrResult) : AnalyzePurchaseInvoiceResult
}

class AnalyzePurchaseInvoiceDocumentUseCase @Inject constructor(
    private val repository: PurchaseRepository,
    private val documentStore: PurchaseDocumentStore,
    private val pdfRenderer: PurchasePdfRenderer,
    private val ocrEngine: PurchaseInvoiceOcrEngine,
    private val parseUseCase: ParsePurchaseInvoiceUseCase,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider
) {
    private companion object {
        const val OCR_PDF_RENDER_MAX_WIDTH_PX = 2048
        const val EVIDENCE_SCHEMA_VERSION = 1
        const val ENGINE_ID = "ML_KIT_TEXT_RECOGNITION_V2_LATIN"
        const val MAX_PAGES = 20
    }

    operator fun invoke(receiptId: PurchaseReceiptId): Flow<AnalyzePurchaseInvoiceResult> = flow {
        try {
            emit(AnalyzePurchaseInvoiceResult.Preparing)

            val receipt = repository.getReceipt(receiptId)
            if (receipt == null) {
                emit(AnalyzePurchaseInvoiceResult.Failure(PurchaseInvoiceOcrFailure.NoDocument))
                return@flow
            }

            val attachmentPath = receipt.attachmentPath
            if (attachmentPath == null) {
                emit(AnalyzePurchaseInvoiceResult.Failure(PurchaseInvoiceOcrFailure.NoDocument))
                return@flow
            }

            val storedDoc = documentStore.inspect(attachmentPath)
            if (storedDoc == null) {
                emit(AnalyzePurchaseInvoiceResult.Failure(PurchaseInvoiceOcrFailure.DocumentMissing))
                return@flow
            }

            // Calculate SHA-256
            val sha256 = try {
                documentStore.open(attachmentPath).use { HashUtils.sha256(it) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emit(AnalyzePurchaseInvoiceResult.Failure(PurchaseInvoiceOcrFailure.Unknown))
                return@flow
            }

            val file = documentStore.getFile(attachmentPath)
            
            val pagesEvidence = mutableListOf<OcrPageEvidence>()

            if (storedDoc.mimeType == "application/pdf") {
                val info = pdfRenderer.inspect(file)
                if (info.failure != null) {
                    emit(AnalyzePurchaseInvoiceResult.Failure(mapPdfFailure(info.failure)))
                    return@flow
                }
                if (info.pageCount > MAX_PAGES) {
                    emit(AnalyzePurchaseInvoiceResult.Failure(PurchaseInvoiceOcrFailure.TooManyPages))
                    return@flow
                }

                for (i in 0 until info.pageCount) {
                    emit(AnalyzePurchaseInvoiceResult.ProcessingPage(i + 1, info.pageCount))
                    val renderResult = pdfRenderer.renderPage(file, i, OCR_PDF_RENDER_MAX_WIDTH_PX)
                    when (renderResult) {
                        is PurchasePdfPageRenderResult.Success -> {
                            try {
                                val evidence = ocrEngine.recognize(renderResult.bitmap)
                                pagesEvidence.add(evidence)
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                emit(AnalyzePurchaseInvoiceResult.Failure(PurchaseInvoiceOcrFailure.RecognitionFailed))
                                return@flow
                            } finally {
                                renderResult.bitmap.recycle()
                            }
                        }
                        is PurchasePdfPageRenderResult.Failure -> {
                            emit(AnalyzePurchaseInvoiceResult.Failure(mapPdfFailure(renderResult.reason)))
                            return@flow
                        }
                    }
                }
            } else if (storedDoc.mimeType.startsWith("image/")) {
                emit(AnalyzePurchaseInvoiceResult.ProcessingPage(1, 1))
                val bitmap = try {
                    SafeImageDecoder.decode(
                        streamProvider = { documentStore.open(attachmentPath) }, 
                        maxDimension = OCR_PDF_RENDER_MAX_WIDTH_PX
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    null
                }

                if (bitmap == null) {
                    emit(AnalyzePurchaseInvoiceResult.Failure(PurchaseInvoiceOcrFailure.ImageDecodeFailed))
                    return@flow
                }

                try {
                    val evidence = ocrEngine.recognize(bitmap)
                    pagesEvidence.add(evidence)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    emit(AnalyzePurchaseInvoiceResult.Failure(PurchaseInvoiceOcrFailure.RecognitionFailed))
                    return@flow
                } finally {
                    bitmap.recycle()
                }
            } else {
                emit(AnalyzePurchaseInvoiceResult.Failure(PurchaseInvoiceOcrFailure.UnsupportedMimeType))
                return@flow
            }

            emit(AnalyzePurchaseInvoiceResult.Saving)

            val resultId = idGenerator.newId()
            val ocrResult = PurchaseInvoiceOcrResult(
                id = resultId,
                purchaseReceiptId = receiptId,
                sourceDocumentSha256 = sha256,
                sourceMimeType = storedDoc.mimeType,
                engine = ENGINE_ID,
                evidenceSchemaVersion = EVIDENCE_SCHEMA_VERSION,
                pageCount = pagesEvidence.size,
                fullText = pagesEvidence.joinToString("\n\n") { it.text },
                processedAt = timeProvider.now()
            )

            val pages = pagesEvidence.mapIndexed { index, evidence ->
                PurchaseInvoiceOcrPage(
                    ocrResultId = resultId,
                    pageIndex = index,
                    widthPx = evidence.widthPx,
                    heightPx = evidence.heightPx,
                    text = evidence.text,
                    evidence = evidence
                )
            }

            try {
                repository.saveOcrResult(
                    result = ocrResult,
                    pages = pages,
                    expectedAttachmentPath = attachmentPath,
                    expectedDocumentSha256 = sha256
                )

                emit(AnalyzePurchaseInvoiceResult.Parsing)
                
                val parseResult = parseUseCase.execute(receiptId)
                if (parseResult.isSuccess) {
                    emit(AnalyzePurchaseInvoiceResult.Success)
                } else {
                    emit(AnalyzePurchaseInvoiceResult.ParseFailed(ocrResult))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (e is IllegalStateException && e.message?.contains("Document changed") == true) {
                    emit(AnalyzePurchaseInvoiceResult.Failure(PurchaseInvoiceOcrFailure.DocumentChanged))
                } else {
                    emit(AnalyzePurchaseInvoiceResult.Failure(PurchaseInvoiceOcrFailure.PersistenceFailed))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(AnalyzePurchaseInvoiceResult.Failure(PurchaseInvoiceOcrFailure.Unknown))
        }
    }

    private fun mapPdfFailure(failure: PurchasePdfRenderFailure): PurchaseInvoiceOcrFailure = when (failure) {
        PurchasePdfRenderFailure.FileMissing -> PurchaseInvoiceOcrFailure.DocumentMissing
        PurchasePdfRenderFailure.CannotOpen -> PurchaseInvoiceOcrFailure.InvalidPdf
        PurchasePdfRenderFailure.InvalidPage -> PurchaseInvoiceOcrFailure.InvalidPdf
        PurchasePdfRenderFailure.RenderFailed -> PurchaseInvoiceOcrFailure.RenderFailed
        PurchasePdfRenderFailure.OutOfMemory -> PurchaseInvoiceOcrFailure.OutOfMemory
        PurchasePdfRenderFailure.Unknown -> PurchaseInvoiceOcrFailure.Unknown
    }
}
