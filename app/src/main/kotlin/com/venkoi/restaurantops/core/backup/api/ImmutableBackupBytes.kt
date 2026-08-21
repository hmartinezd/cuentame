package com.venkoi.restaurantops.core.backup.api

import java.io.OutputStream
import java.security.MessageDigest

class ImmutableBackupBytes private constructor(
    bytes: ByteArray
) {
    private val data = bytes.copyOf()

    val size: Int
        get() = data.size

    fun writeTo(outputStream: OutputStream) {
        outputStream.write(data)
    }

    fun sha256(): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(data)
            .joinToString("") { "%02x".format(it) }
    }

    fun copyForTest(): ByteArray = data.copyOf()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ImmutableBackupBytes
        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        return data.contentHashCode()
    }

    companion object {
        fun from(bytes: ByteArray): ImmutableBackupBytes = ImmutableBackupBytes(bytes)
    }
}
