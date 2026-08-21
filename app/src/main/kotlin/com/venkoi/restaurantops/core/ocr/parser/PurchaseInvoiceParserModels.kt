package com.venkoi.restaurantops.core.ocr.parser

import com.venkoi.restaurantops.core.common.serialization.BigDecimalSerializer
import com.venkoi.restaurantops.core.common.serialization.LocalDateSerializer
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
    InferredColumnLayout,
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
    val isIgnored: Boolean = false,
    val correction: ParsedInvoiceLineCorrection? = null
) {
    companion object {
        /** Truthful provenance for a reviewer-added line: values are corrections, never OCR. */
        fun manual(index: Int) = ParsedInvoiceLineCandidate(
            index = index,
            vendorCode = ParsedField(null, null, null),
            description = ParsedField(null, null, null),
            quantity = ParsedField(null, null, null),
            packageText = ParsedField(null, null, null),
            unitPrice = ParsedField(null, null, null),
            lineTotal = ParsedField(null, null, null),
            confidence = null,
            evidenceRefs = emptyList()
        )
    }
}

@Serializable
data class PurchaseInvoiceParseResult(
    val id: String = "",
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
    val warnings: List<InvoiceParseWarning> = emptyList(),
    val corrections: PurchaseInvoiceCorrections? = null
)

@Serializable
data class Correction<T>(
    val value: T?,
    val isExplicitlyCleared: Boolean = false
)

@Serializable
data class PurchaseInvoiceCorrections(
    val supplierName: Correction<String?>? = null,
    val invoiceNumber: Correction<String?>? = null,
    val invoiceDate: Correction<@Serializable(with = LocalDateSerializer::class) LocalDate?>? = null,
    val subtotal: Correction<@Serializable(with = BigDecimalSerializer::class) BigDecimal?>? = null,
    val discount: Correction<@Serializable(with = BigDecimalSerializer::class) BigDecimal?>? = null,
    val fees: Correction<@Serializable(with = BigDecimalSerializer::class) BigDecimal?>? = null,
    val tax: Correction<@Serializable(with = BigDecimalSerializer::class) BigDecimal?>? = null,
    val total: Correction<@Serializable(with = BigDecimalSerializer::class) BigDecimal?>? = null
)

@Serializable
data class ParsedInvoiceLineCorrection(
    val vendorCode: Correction<String?>? = null,
    val description: Correction<String?>? = null,
    val quantity: Correction<@Serializable(with = BigDecimalSerializer::class) BigDecimal?>? = null,
    val packageText: Correction<String?>? = null,
    val unitPrice: Correction<@Serializable(with = BigDecimalSerializer::class) BigDecimal?>? = null,
    val lineTotal: Correction<@Serializable(with = BigDecimalSerializer::class) BigDecimal?>? = null
)

fun <T> ParsedField<T>.effectiveValue(correction: Correction<T>?): T? {
    if (correction == null) return normalizedValue
    return if (correction.isExplicitlyCleared) null else correction.value
}

fun <T> ParsedField<T>.isEdited(correction: Correction<T>?): Boolean = correction != null
