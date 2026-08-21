package com.venkoi.cuentame.feature.sales

import android.content.Context
import android.net.Uri
import com.venkoi.cuentame.core.database.repository.MAX_SALES_EXPORT_BYTES
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

sealed interface SalesDocumentReadResult {
    data class Success(val bytes: ByteArray) : SalesDocumentReadResult
    data object FileTooLarge : SalesDocumentReadResult
    data object PermissionDenied : SalesDocumentReadResult
    data object Unreadable : SalesDocumentReadResult
}

class SalesDocumentReader @Inject constructor(@ApplicationContext context: Context) {
    private val resolver = context.contentResolver
    fun read(uri: Uri): SalesDocumentReadResult = try {
        resolver.openInputStream(uri)?.use(::readSalesBytes) ?: SalesDocumentReadResult.Unreadable
    } catch (_: SecurityException) { SalesDocumentReadResult.PermissionDenied }
      catch (_: FileNotFoundException) { SalesDocumentReadResult.Unreadable }
      catch (_: IOException) { SalesDocumentReadResult.Unreadable }

}
internal fun readSalesBytes(input: InputStream, limit: Int = MAX_SALES_EXPORT_BYTES): SalesDocumentReadResult {
        val output = java.io.ByteArrayOutputStream(minOf(limit, 8192))
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, limit + 1 - total))
            if (count < 0) return SalesDocumentReadResult.Success(output.toByteArray())
            total += count
            if (total > limit) return SalesDocumentReadResult.FileTooLarge
            output.write(buffer, 0, count)
        }
}
