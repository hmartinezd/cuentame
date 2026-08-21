package com.venkoi.restaurantops.core.backup

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.backup.api.BackupByteMath
import com.venkoi.restaurantops.core.backup.api.BackupSizeOverflowException
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupByteMathTest {

    @Test
    fun addExact_success() {
        assertThat(BackupByteMath.addExact(100L, 200L)).isEqualTo(300L)
        assertThat(BackupByteMath.addExact(0L, 0L)).isEqualTo(0L)
    }

    @Test
    fun addExact_overflow_throws() {
        assertThrows(BackupSizeOverflowException::class.java) {
            BackupByteMath.addExact(Long.MAX_VALUE - 10, 20)
        }
    }

    @Test
    fun addExact_rejectsNegative() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupByteMath.addExact(-1, 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupByteMath.addExact(10, -1)
        }
    }
}
