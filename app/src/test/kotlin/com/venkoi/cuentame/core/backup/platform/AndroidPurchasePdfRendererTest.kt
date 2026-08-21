package com.venkoi.cuentame.core.backup.platform

import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.backup.api.PurchasePdfRenderFailure
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

class AndroidPurchasePdfRendererTest {

    private val renderer = AndroidPurchasePdfRenderer()

    @Test
    fun `inspect missing file returns FileMissing`() = runTest {
        val file = File("non_existent_file.pdf")
        val info = renderer.inspect(file)
        
        assertThat(info.pageCount).isEqualTo(0)
        assertThat(info.failure).isEqualTo(PurchasePdfRenderFailure.FileMissing)
    }

    @Test
    fun `renderPage missing file returns FileMissing`() = runTest {
        val file = File("non_existent_file.pdf")
        val result = renderer.renderPage(file, 0, 1024)
        
        assertThat(result).isInstanceOf(com.venkoi.cuentame.core.backup.api.PurchasePdfPageRenderResult.Failure::class.java)
        val failure = result as com.venkoi.cuentame.core.backup.api.PurchasePdfPageRenderResult.Failure
        assertThat(failure.reason).isEqualTo(PurchasePdfRenderFailure.FileMissing)
    }

    @Test
    fun `inspect invalid file returns CannotOpen`() = runTest {
        // Create a fake file that is not a PDF
        val file = File.createTempFile("invalid", ".pdf")
        file.writeText("Not a PDF")
        
        try {
            val info = renderer.inspect(file)
            // On JVM, ParcelFileDescriptor.open might fail differently, but we expect CannotOpen
            assertThat(info.failure).isEqualTo(PurchasePdfRenderFailure.CannotOpen)
        } finally {
            file.delete()
        }
    }
}
