package com.miara.cuentame.core.model.purchase.materialization

import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.IngredientUnitOptionId
import com.miara.cuentame.core.common.ids.InventoryAreaId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.core.ocr.parser.InvoiceParseWarning
import java.math.BigDecimal
import java.time.LocalDate

data class PurchaseInvoiceDraftProposal(
    val purchaseReceiptId: PurchaseReceiptId,
    val parseResultId: String,
    val sourceDocumentSha256: String,
    val supplierProposal: SupplierProposal?,
    val invoiceNumber: String?,
    val invoiceDate: LocalDate?,
    val subtotal: BigDecimal?,
    val discount: BigDecimal?,
    val fees: BigDecimal?,
    val tax: BigDecimal?,
    val total: BigDecimal?,
    val lines: List<PurchaseInvoiceLineProposal>,
    val warnings: List<InvoiceParseWarning> = emptyList(),
    val blockingIssues: List<MaterializationBlockingIssue> = emptyList()
)

data class SupplierProposal(
    val id: SupplierId,
    val name: String
)

data class PurchaseInvoiceLineProposal(
    val lineIndex: Int,
    val ingredientId: IngredientId,
    val ingredientName: String,
    val unitOptionId: IngredientUnitOptionId,
    val unitOptionName: String,
    val areaId: InventoryAreaId,
    val areaName: String,
    val quantityEntered: BigDecimal,
    val quantityBase: BigDecimal,
    val unitPrice: BigDecimal?,
    val lineTotal: BigDecimal,
    val warnings: List<InvoiceParseWarning> = emptyList()
)

enum class MaterializationBlockingIssue {
    UnresolvedLines,
    MissingSupplier,
    PurchaseAlreadyPosted,
    DocumentChanged,
    ParseChanged
}
