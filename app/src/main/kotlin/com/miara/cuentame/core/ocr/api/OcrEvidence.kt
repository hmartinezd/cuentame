package com.miara.cuentame.core.ocr.api

import kotlinx.serialization.Serializable

@Serializable
data class OcrPageEvidence(
    val widthPx: Int,
    val heightPx: Int,
    val text: String,
    val blocks: List<OcrBlockEvidence>
)

@Serializable
data class OcrBlockEvidence(
    val text: String,
    val boundingBox: OcrRect?,
    val cornerPoints: List<OcrPoint>,
    val recognizedLanguages: List<String>,
    val lines: List<OcrLineEvidence>
)

@Serializable
data class OcrLineEvidence(
    val text: String,
    val boundingBox: OcrRect?,
    val cornerPoints: List<OcrPoint> = emptyList(),
    val confidence: Float? = null,
    val angleDegrees: Float? = null,
    val recognizedLanguages: List<String> = emptyList(),
    val elements: List<OcrElementEvidence>
)

@Serializable
data class OcrElementEvidence(
    val text: String,
    val boundingBox: OcrRect?,
    val cornerPoints: List<OcrPoint> = emptyList(),
    val confidence: Float? = null,
    val angleDegrees: Float? = null,
    val recognizedLanguages: List<String> = emptyList()
)

@Serializable
data class OcrRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

@Serializable
data class OcrPoint(
    val x: Int,
    val y: Int
)
