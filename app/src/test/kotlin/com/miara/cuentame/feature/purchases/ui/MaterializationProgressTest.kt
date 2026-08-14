package com.miara.cuentame.feature.purchases.ui

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.model.purchase.materialization.MaterializationBlockingIssue
import com.miara.cuentame.core.model.purchase.materialization.PurchaseInvoiceDraftProposal
import com.miara.cuentame.core.model.purchase.materialization.PurchaseInvoiceLineProposal
import org.junit.Test

class MaterializationProgressTest {

    @Test
    fun `partially complete proposal reports completed and remaining active items`() {
        val progress = materializationProgress(proposal(2, 7))

        assertThat(progress).isEqualTo(MaterializationProgress(9, 2, 7))
        assertThat(materializationCanApply(proposal(2, 7))).isFalse()
    }

    @Test
    fun `all complete proposal has no remaining items`() {
        val progress = materializationProgress(proposal(9, 0))

        assertThat(progress).isEqualTo(MaterializationProgress(9, 9, 0))
        assertThat(materializationCanApply(proposal(9, 0))).isTrue()
    }

    @Test
    fun `denominator uses proposal lines and therefore excludes ignored source lines`() {
        // The source had ten OCR lines, but its ignored line is absent from the proposal.
        val progress = materializationProgress(proposal(9, 0))

        assertThat(progress.activeCount).isEqualTo(9)
    }

    @Test
    fun `line with confirmed match data but blocking reason is not complete`() {
        val proposal = proposal(8, 0).copy(
            lines = proposal(8, 0).lines + line(MaterializationBlockingIssue.MissingQuantity),
            blockingIssues = listOf(MaterializationBlockingIssue.UnresolvedLines)
        )

        assertThat(materializationProgress(proposal)).isEqualTo(MaterializationProgress(9, 8, 1))
    }

    @Test
    fun `global supplier blocker does not change accurate line completion`() {
        val proposal = proposal(9, 0).copy(
            blockingIssues = listOf(MaterializationBlockingIssue.MissingSupplier)
        )

        assertThat(materializationProgress(proposal)).isEqualTo(MaterializationProgress(9, 9, 0))
        assertThat(proposal.blockingIssues).contains(MaterializationBlockingIssue.MissingSupplier)
        assertThat(materializationCanApply(proposal)).isFalse()
    }

    private fun proposal(complete: Int, blocked: Int) = PurchaseInvoiceDraftProposal(
        purchaseReceiptId = PurchaseReceiptId("receipt"),
        parseResultId = "parse",
        sourceDocumentSha256 = "sha",
        sourceStateFingerprint = "fingerprint",
        supplierProposal = null,
        invoiceNumber = null,
        invoiceDate = null,
        subtotal = null,
        discount = null,
        fees = null,
        tax = null,
        total = null,
        lines = List(complete) { line(null) } +
            List(blocked) { line(MaterializationBlockingIssue.UnresolvedMatch) },
        blockingIssues = if (blocked > 0) listOf(MaterializationBlockingIssue.UnresolvedLines) else emptyList()
    )

    private fun line(blockingReason: MaterializationBlockingIssue?) = PurchaseInvoiceLineProposal(
        lineIndex = 0,
        ingredientId = null,
        ingredientName = null,
        unitOptionId = null,
        unitOptionName = null,
        areaId = null,
        areaName = null,
        quantityEntered = null,
        quantityBase = null,
        factorToBase = null,
        baseUnitSymbol = null,
        unitPrice = null,
        lineTotal = null,
        blockingReason = blockingReason
    )
}
