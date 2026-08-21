package com.venkoi.restaurantops.core.backup.api

class BackupSizeOverflowException : Exception("Backup size calculation overflowed Long.MAX_VALUE")

object BackupByteMath {

    fun addExact(left: Long, right: Long): Long {
        require(left >= 0) { "left must be non-negative" }
        require(right >= 0) { "right must be non-negative" }

        if (Long.MAX_VALUE - left < right) {
            throw BackupSizeOverflowException()
        }

        return left + right
    }
}
