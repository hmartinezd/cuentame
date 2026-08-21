package com.venkoi.cuentame.core.backup

import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.core.backup.api.ImmutableBackupBytes
import org.junit.Test
import java.security.MessageDigest

class ChecksumTest {

    @Test
    fun `ImmutableBackupBytes sha256 matches manual digest`() {
        val data = "Hello World".toByteArray()
        val bytes = ImmutableBackupBytes.from(data)
        
        val manual = MessageDigest.getInstance("SHA-256")
            .digest(data)
            .joinToString("") { "%02x".format(it) }
            
        assertThat(bytes.sha256()).isEqualTo(manual)
    }

    @Test
    fun `sha256 is deterministic`() {
        val bytes = ImmutableBackupBytes.from("data".toByteArray())
        assertThat(bytes.sha256()).isEqualTo(bytes.sha256())
    }
}
