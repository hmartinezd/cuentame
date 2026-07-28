package com.miara.cuentame.core.backup

import android.net.Uri
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject

interface ChecksumProvider {
    fun calculateChecksum(inputStream: InputStream): String
    fun computeAttachmentId(uri: Uri): String
    fun computeAttachmentId(uriString: String): String
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

    override fun computeAttachmentId(uri: Uri): String {
        return computeAttachmentId(uri.toString())
    }

    override fun computeAttachmentId(uriString: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(uriString.toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }.substring(0, 16)
    }
}
