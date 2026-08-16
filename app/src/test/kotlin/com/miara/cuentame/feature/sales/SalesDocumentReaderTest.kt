package com.miara.cuentame.feature.sales
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
class SalesDocumentReaderTest {
 @Test fun exactLimitSucceeds(){assertThat((readSalesBytes(ByteArrayInputStream(byteArrayOf(1,2)),2) as SalesDocumentReadResult.Success).bytes).isEqualTo(byteArrayOf(1,2))}
 @Test fun limitPlusOneRejects(){assertThat(readSalesBytes(ByteArrayInputStream(byteArrayOf(1,2,3)),2)).isEqualTo(SalesDocumentReadResult.FileTooLarge)}
}
