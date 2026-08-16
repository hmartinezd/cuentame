package com.miara.cuentame.core.ocr.fixture

import com.miara.cuentame.core.ocr.api.OcrPageEvidence
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object OcrEvidenceFixtureLoader {
    private val json = Json {
        ignoreUnknownKeys = false
        coerceInputValues = false
    }

    fun loadPages(fixtureDirectory: String): List<OcrPageEvidence> {
        val normalized = fixtureDirectory.trim('/').takeIf { it.isNotBlank() }
            ?: error("OCR fixture directory must not be blank")
        val classLoader = checkNotNull(javaClass.classLoader) { "Test class loader is unavailable" }
        val indexPath = "ocr-fixtures/$normalized/pages.txt"
        val index = classLoader.getResourceAsStream(indexPath)
            ?: error("Missing OCR fixture index: $indexPath")
        val pageFiles = index.bufferedReader().useLines { lines ->
            lines.map(String::trim).filter { it.isNotEmpty() && !it.startsWith('#') }.toList()
        }
        require(pageFiles.isNotEmpty()) { "OCR fixture index is empty: $indexPath" }

        return pageFiles.mapIndexed { pageIndex, filename ->
            val resourcePath = "ocr-fixtures/$normalized/$filename"
            val content = classLoader.getResourceAsStream(resourcePath)?.bufferedReader()?.use { it.readText() }
                ?: error("Missing OCR fixture page ${pageIndex + 1}: $resourcePath")
            try {
                json.decodeFromString<OcrPageEvidence>(content)
            } catch (failure: Exception) {
                throw AssertionError("Invalid OCR fixture page ${pageIndex + 1}: $resourcePath", failure)
            }
        }
    }
}
