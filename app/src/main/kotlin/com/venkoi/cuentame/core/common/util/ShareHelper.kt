package com.venkoi.cuentame.core.common.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object ShareHelper {
    fun shareCsv(context: Context, filename: String, content: String, title: String): Result<Unit> {
        return shareTextFile(context, filename, content, "text/csv", title)
    }

    fun shareTextFile(
        context: Context,
        filename: String,
        content: String,
        mimeType: String,
        title: String
    ): Result<Unit> = shareFile(context, filename, content.toByteArray(Charsets.UTF_8), mimeType, title)

    fun shareFile(
        context: Context,
        filename: String,
        content: ByteArray,
        mimeType: String,
        title: String
    ): Result<Unit> = runCatching {
        val cacheDir = File(context.cacheDir, "exports")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        
        // Clean up old exports
        cacheDir.listFiles()?.forEach { it.delete() }

        val file = File(cacheDir, filename)
        file.writeBytes(content)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, filename)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, title))
    }

    fun shareTextFiles(
        context: Context,
        files: List<Pair<String, String>>,
        mimeType: String,
        title: String
    ): Result<Unit> = runCatching {
        require(files.isNotEmpty()) { "At least one file is required" }
        val cacheDir = File(context.cacheDir, "exports")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        cacheDir.listFiles()?.forEach { it.delete() }

        val uris = ArrayList(files.map { (filename, content) ->
            val file = File(cacheDir, filename)
            file.writeText(content)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        })

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
}
