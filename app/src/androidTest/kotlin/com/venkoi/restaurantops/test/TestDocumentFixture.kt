package com.venkoi.restaurantops.test

import android.content.Context
import android.net.Uri
import com.venkoi.restaurantops.core.backup.api.PurchaseDocumentStore
import com.venkoi.restaurantops.core.backup.api.StoredPurchaseDocument
import com.venkoi.restaurantops.core.common.ids.PurchaseReceiptId
import java.io.File
import java.security.MessageDigest

object TestDocumentFixture {

    fun createMinimalPdf(file: File) {
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(100, 100, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        page.canvas.drawText("Test PDF content", 10f, 10f, android.graphics.Paint())
        pdfDocument.finishPage(page)
        file.outputStream().use { 
            pdfDocument.writeTo(it)
        }
        pdfDocument.close()
    }

    suspend fun storeTestDocument(
        context: Context,
        documentStore: PurchaseDocumentStore,
        receiptId: PurchaseReceiptId
    ): StoredPurchaseDocument {
        val tempFile = File(context.cacheDir, "test_invoice_${System.currentTimeMillis()}.pdf")
        createMinimalPdf(tempFile)
        val uri = Uri.fromFile(tempFile)
        
        return try {
            documentStore.importDocument(receiptId, uri, "test_invoice.pdf")
        } finally {
            tempFile.delete()
        }
    }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var n: Int
            while (input.read(buffer).also { n = it } != -1) {
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
