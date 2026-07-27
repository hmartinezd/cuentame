package com.miara.cuentame.core.backup

import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject

interface ChecksumProvider {
    fun calculateChecksum(inputStream: InputStream): String
}

class Sha256ChecksumProvider @Inject constructor() : ChecksumProvider {
    override fun calculateChecksum(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
