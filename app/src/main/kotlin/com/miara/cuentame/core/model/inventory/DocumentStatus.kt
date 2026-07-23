package com.miara.cuentame.core.model.inventory

enum class DocumentStatus {
    DRAFT,
    POSTED,
    VOIDED
}

enum class StockCountStatus {
    DRAFT,
    COMPLETED,
    VOIDED
}

enum class CountAreaStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED
}

enum class WasteReason {
    EXPIRED,
    SPOILED,
    DAMAGED,
    DROPPED,
    OVER_PORTIONED,
    THEFT,
    OTHER
}

enum class InventoryMovementType {
    PURCHASE,
    WASTE,
    COUNT_ADJUSTMENT,
    MANUAL_ADJUSTMENT,
    OPENING_BALANCE,
    REVERSAL
}

enum class SourceDocumentType {
    PURCHASE_RECEIPT,
    STOCK_COUNT,
    WASTE_EVENT,
    MANUAL
}
