package com.miara.cuentame.core.common.time

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class TimeProviderTest {
    private val provider = SystemTimeProvider()

    @Test
    fun `now returns current time`() {
        val before = Instant.now()
        val now = provider.now()
        val after = Instant.now()
        
        assertThat(now).isAtLeast(before)
        assertThat(now).isAtMost(after)
    }
}
