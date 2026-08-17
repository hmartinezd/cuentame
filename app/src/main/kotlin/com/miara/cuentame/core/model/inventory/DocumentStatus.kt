package com.miara.cuentame.core.model.inventory

enum class DocumentStatus {
    DRAFT,
    POSTED,
    VOIDED,
    UNKNOWN
}

enum class StockCountStatus {
    DRAFT,
    COMPLETED,
    VOIDED,
    UNKNOWN
}

enum class CountAreaStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    UNKNOWN
}

enum class WasteReason {
    EXPIRED,
    SPOILED,
    PREPARATION_ERROR,
    OVERPRODUCTION,
    DROPPED_OR_DAMAGED,
    CUSTOMER_RETURN,
    QUALITY_REJECTION,
    OTHER,
    UNKNOWN
}

enum class InventoryMovementType {
    PURCHASE,
    WASTE,
    COUNT_ADJUSTMENT,
    MANUAL_ADJUSTMENT,
    OPENING_BALANCE,
    REVERSAL,
    PRODUCTION_CONSUMPTION,
    PRODUCTION_OUTPUT,
    SALES_CONSUMPTION,
    UNKNOWN
}

enum class SourceDocumentType {
    PURCHASE_RECEIPT,
    STOCK_COUNT,
    WASTE_EVENT,
    MANUAL,
    PRODUCTION_BATCH,
    SALES_TRANSACTION,
    UNKNOWN
}
