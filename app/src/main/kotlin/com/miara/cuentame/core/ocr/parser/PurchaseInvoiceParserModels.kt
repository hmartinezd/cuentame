package com.miara.cuentame.core.ocr.parser

import com.miara.cuentame.core.common.serialization.BigDecimalSerializer
import com.miara.cuentame.core.common.serialization.LocalDateSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDate

@Serializable
data class OcrEvidenceRef(
    val pageIndex: Int,
    val blockIndex: Int? = null,
    val lineIndex: Int? = null,
    val elementIndex: Int? = null
)

@Serializable
enum class InvoiceParseWarning {
    LowConfidence,
    AmbiguousDate,
    AmbiguousNumberFormat,
    MissingDescription,
    MissingQuantity,
    MissingLineTotal,
    LineMathMismatch,
    InvoiceMathMismatch,
    UnknownColumnLayout,
    WrappedRowUncertain,
    PossibleFooterNoise,
    PossibleDuplicate,
    UnsupportedPattern
}

@Serializable
enum class ConfidenceBand {
    High, Medium, Low
}

@Serializable
data class ParsedField<T>(
    val detectedText: String?,
    val normalizedValue: T?,
    val confidence: Float?,
    val evidenceRefs: List<OcrEvidenceRef> = emptyList(),
    val warnings: List<InvoiceParseWarning> = emptyList()
) {
    val confidenceBand: ConfidenceBand
        get() = when {
            confidence == null -> ConfidenceBand.Low
            confidence >= 0.85f -> ConfidenceBand.High
            confidence >= 0.60f -> ConfidenceBand.Medium
            else -> ConfidenceBand.Low
        }
}

@Serializable
data class ParsedInvoiceLineCandidate(
    val index: Int,
    val vendorCode: ParsedField<String?>,
    val description: ParsedField<String?>,
    val quantity: ParsedField<@Serializable(with = BigDecimalSerializer::class) BigDecimal?>,
    val packageText: ParsedField<String?>,
    val unitPrice: ParsedField<@Serializable(with = BigDecimalSerializer::class) BigDecimal?>,
    val lineTotal: ParsedField<@Serializable(with = BigDecimalSerializer::class) BigDecimal?>,
    val confidence: Float?,
    val evidenceRefs: List<OcrEvidenceRef> = emptyList(),
    val warnings: List<InvoiceParseWarning> = emptyList(),
    val isIgnored: Boolean = false
)

@Serializable
data class PurchaseInvoiceParseResult(
    val supplierNameCandidate: ParsedField<String?>,
    val invoiceNumber: ParsedField<String?>,
    val invoiceDate: ParsedField<@Serializable(with = LocalDateSerializer::class) LocalDate?>,
    val subtotal: ParsedField<@Serializable(with = BigDecimalSerializer::class) BigDecimal?>,
    val discount: ParsedField<@Serializable(with = BigDecimalSerializer::class) BigDecimal?>,
    val fees: ParsedField<@Serializable(with = BigDecimalSerializer::class) BigDecimal?>,
    val tax: ParsedField<@Serializable(with = BigDecimalSerializer::class) BigDecimal?>,
    val total: ParsedField<@Serializable(with = BigDecimalSerializer::class) BigDecimal?>,
    val currency: ParsedField<String?>,
    val lines: List<ParsedInvoiceLineCandidate>,
    val confidence: Float?,
    val warnings: List<InvoiceParseWarning> = emptyList()
)
