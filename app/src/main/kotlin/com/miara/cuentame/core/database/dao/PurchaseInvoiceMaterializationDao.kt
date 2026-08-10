package com.miara.cuentame.core.database.dao

import androidx.room.*
import com.miara.cuentame.core.database.entity.PurchaseInvoiceDraftApplicationEntity
import com.miara.cuentame.core.database.entity.PurchaseInvoiceLineOriginEntity
import com.miara.cuentame.core.database.entity.PurchaseLineEntity
import com.miara.cuentame.core.database.entity.PurchaseReceiptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseInvoiceMaterializationDao {

    @Query("SELECT * FROM purchase_invoice_draft_applications WHERE purchaseReceiptId = :receiptId")
    suspend fun getApplicationForReceipt(receiptId: String): PurchaseInvoiceDraftApplicationEntity?

    @Query("SELECT * FROM purchase_invoice_draft_applications WHERE purchaseReceiptId = :receiptId")
    fun observeApplicationForReceipt(receiptId: String): Flow<PurchaseInvoiceDraftApplicationEntity?>

    @Query("SELECT * FROM purchase_invoice_line_origins WHERE applicationId = :applicationId")
    suspend fun getLineOrigins(applicationId: String): List<PurchaseInvoiceLineOriginEntity>

    @Upsert
    suspend fun upsertApplication(application: PurchaseInvoiceDraftApplicationEntity)

    @Upsert
    suspend fun upsertLineOrigins(origins: List<PurchaseInvoiceLineOriginEntity>)

    @Query("DELETE FROM purchase_invoice_draft_applications WHERE id = :applicationId")
    suspend fun deleteApplication(applicationId: String)

    @Query("DELETE FROM purchase_invoice_line_origins WHERE applicationId = :applicationId")
    suspend fun deleteLineOrigins(applicationId: String)

    @Query("DELETE FROM purchase_invoice_line_origins WHERE purchaseLineId = :purchaseLineId")
    suspend fun deleteLineOrigin(purchaseLineId: String)
}
