package com.miara.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream

class ChecksumTest {

    private val checksumProvider = Sha256ChecksumProvider()

    @Test
    fun `calculateChecksum produces correct SHA-256`() {
        val input = "test".toByteArray(Charsets.UTF_8)
        val checksum = checksumProvider.calculateChecksum(ByteArrayInputStream(input))
        
        assertThat(checksum).isEqualTo("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
    }

    @Test
    fun `calculateChecksum handles empty input`() {
        val checksum = checksumProvider.calculateChecksum(ByteArrayInputStream(ByteArray(0)))
        
        // echo -n "" | shasum -a 256
        // e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        assertThat(checksum).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    }
}
