package com.miara.cuentame.core.domain.usecase.purchase

import com.miara.cuentame.core.domain.repository.PurchaseRepository
import com.miara.cuentame.core.model.purchase.materialization.PurchaseInvoiceDraftProposal
import com.miara.cuentame.core.model.purchase.materialization.failure.PurchaseInvoiceMaterializationResult
import com.miara.cuentame.core.model.purchase.materialization.failure.PurchaseInvoiceMaterializationFailure
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ApplyInvoiceToPurchaseDraftUseCaseTest {

    private val purchaseRepository = mockk<PurchaseRepository>()
    private lateinit var useCase: ApplyInvoiceToPurchaseDraftUseCase

    @Before
    fun setup() {
        useCase = ApplyInvoiceToPurchaseDraftUseCase(purchaseRepository)
    }

    @Test
    fun `executes delegation to repository`() = runBlocking {
        val proposal = createProposal()
        val expectedResult = PurchaseInvoiceMaterializationResult.Success
        
        coEvery { purchaseRepository.applyInvoiceToDraft(proposal) } returns expectedResult

        val result = useCase.execute(proposal)

        assertEquals(expectedResult, result)
        coVerify { purchaseRepository.applyInvoiceToDraft(proposal) }
    }

    @Test
    fun `returns failure when repository fails`() = runBlocking {
        val proposal = createProposal()
        val expectedResult = PurchaseInvoiceMaterializationResult.Failure(PurchaseInvoiceMaterializationFailure.ManualEditConflict)
        
        coEvery { purchaseRepository.applyInvoiceToDraft(proposal) } returns expectedResult

        val result = useCase.execute(proposal)

        assertEquals(expectedResult, result)
    }

    private fun createProposal() = PurchaseInvoiceDraftProposal(
        purchaseReceiptId = PurchaseReceiptId("r1"),
        parseResultId = "p1",
        sourceDocumentSha256 = "sha",
        sourceStateFingerprint = "fingerprint",
        supplierProposal = null,
        invoiceNumber = "INV1",
        invoiceDate = LocalDate.now(),
        subtotal = BigDecimal("100"),
        discount = null,
        fees = null,
        tax = null,
        total = BigDecimal("100"),
        lines = emptyList()
    )
}
