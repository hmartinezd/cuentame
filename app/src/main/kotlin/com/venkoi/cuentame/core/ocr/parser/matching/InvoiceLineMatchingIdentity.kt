package com.venkoi.cuentame.core.ocr.parser.matching

/**
 * Encapsulates the fields that define the "identity" of an invoice line for inventory matching.
 * Changes to these fields (via OCR corrections or resets) should trigger re-matching.
 */
data class InvoiceLineMatchingIdentity(
    val normalizedVendorCode: String,
    val normalizedDescription: String,
    val normalizedPackageText: String
) {
    companion object {
        fun from(
            vendorCode: String?,
            description: String?,
            packageText: String?
        ): InvoiceLineMatchingIdentity {
            return InvoiceLineMatchingIdentity(
                normalizedVendorCode = InventoryNormalization.normalizeVendorCode(vendorCode),
                normalizedDescription = InventoryNormalization.normalizeDescription(description),
                normalizedPackageText = InventoryNormalization.normalizePackageText(packageText)
            )
        }
    }
}
