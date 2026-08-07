package com.miara.cuentame.core.ocr.api

/**
 * Categories for OCR failures.
 */
enum class PurchaseInvoiceOcrFailure {
    NoDocument,
    DocumentMissing,
    UnsupportedMimeType,
    InvalidPdf,
    TooManyPages,
    ImageDecodeFailed,
    RenderFailed,
    RecognizerUnavailable,
    RecognitionFailed,
    DocumentChanged,
    PersistenceFailed,
    OutOfMemory,
    Unknown
}
