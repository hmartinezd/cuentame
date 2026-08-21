package com.venkoi.restaurantops.core.model.salesexport

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

enum class SalesExportValidationCode {
    WRONG_FORMAT, UNSUPPORTED_VERSION,
    BLANK_EXPORT_ID, BLANK_TERMINAL_ID, BLANK_RESTAURANT_ID,
    INVALID_GENERATED_AT, INVALID_BUSINESS_DATE,
    BLANK_MENU_PACKAGE_ID, BLANK_MENU_ID, INVALID_PUBLICATION_REVISION, INVALID_CURRENCY,
    NO_TRANSACTIONS, BLANK_TRANSACTION_ID, INVALID_OPENED_AT, INVALID_CLOSED_AT,
    CLOSED_BEFORE_OPENED, INVALID_TRANSACTION_STATUS, NO_LINES, DUPLICATE_TRANSACTION_ID,
    BLANK_SALE_LINE_ID, BLANK_SELLABLE_ITEM_ID, BLANK_ITEM_NAME,
    INVALID_QUANTITY, INVALID_UNIT_PRICE, INVALID_GROSS, INVALID_DISCOUNT, INVALID_NET,
    NEGATIVE_COMMERCIAL_REVISION, NEGATIVE_CONSUMPTION_REVISION,
    DISCOUNT_EXCEEDS_GROSS, GROSS_MISMATCH, NET_MISMATCH, DUPLICATE_SALE_LINE_ID
}

data class SalesExportValidationFailure(val code: SalesExportValidationCode)

object SalesExportValidator {
    fun validate(value: SalesExportV1): SalesExportValidationFailure? {
        if (value.format != SALES_EXPORT_FORMAT) return failure(SalesExportValidationCode.WRONG_FORMAT)
        if (value.formatVersion != SALES_EXPORT_FORMAT_VERSION) return failure(SalesExportValidationCode.UNSUPPORTED_VERSION)
        if (value.exportId.isBlank()) return failure(SalesExportValidationCode.BLANK_EXPORT_ID)
        if (value.terminalId.isBlank()) return failure(SalesExportValidationCode.BLANK_TERMINAL_ID)
        if (value.restaurantId.isBlank()) return failure(SalesExportValidationCode.BLANK_RESTAURANT_ID)
        if (value.generatedAt.instantOrNull() == null) return failure(SalesExportValidationCode.INVALID_GENERATED_AT)
        if (runCatching { LocalDate.parse(value.businessDate) }.isFailure) return failure(SalesExportValidationCode.INVALID_BUSINESS_DATE)
        if (value.menuPackageId.isBlank()) return failure(SalesExportValidationCode.BLANK_MENU_PACKAGE_ID)
        if (value.menuId.isBlank()) return failure(SalesExportValidationCode.BLANK_MENU_ID)
        if (value.publicationRevision <= 0) return failure(SalesExportValidationCode.INVALID_PUBLICATION_REVISION)
        if (runCatching { Currency.getInstance(value.currency) }.isFailure) return failure(SalesExportValidationCode.INVALID_CURRENCY)
        if (value.transactions.isEmpty()) return failure(SalesExportValidationCode.NO_TRANSACTIONS)

        val transactionIds = mutableSetOf<String>()
        val lineIds = mutableSetOf<String>()
        value.transactions.forEach { transaction ->
            if (transaction.transactionId.isBlank()) return failure(SalesExportValidationCode.BLANK_TRANSACTION_ID)
            val openedAt = transaction.openedAt.instantOrNull() ?: return failure(SalesExportValidationCode.INVALID_OPENED_AT)
            val closedAt = transaction.closedAt.instantOrNull() ?: return failure(SalesExportValidationCode.INVALID_CLOSED_AT)
            if (closedAt < openedAt) return failure(SalesExportValidationCode.CLOSED_BEFORE_OPENED)
            if (transaction.status !in setOf("COMPLETED", "VOIDED")) return failure(SalesExportValidationCode.INVALID_TRANSACTION_STATUS)
            if (transaction.lines.isEmpty()) return failure(SalesExportValidationCode.NO_LINES)
            if (!transactionIds.add(transaction.transactionId)) return failure(SalesExportValidationCode.DUPLICATE_TRANSACTION_ID)

            transaction.lines.forEach { line ->
                if (line.saleLineId.isBlank()) return failure(SalesExportValidationCode.BLANK_SALE_LINE_ID)
                if (line.sellableItemId.isBlank()) return failure(SalesExportValidationCode.BLANK_SELLABLE_ITEM_ID)
                if (line.displayNameSnapshot.isBlank()) return failure(SalesExportValidationCode.BLANK_ITEM_NAME)
                val quantity = line.quantity.decimalOrNull()?.takeIf { it > BigDecimal.ZERO }
                    ?: return failure(SalesExportValidationCode.INVALID_QUANTITY)
                val unitPrice = line.unitPrice.nonNegativeDecimalOrNull()
                    ?: return failure(SalesExportValidationCode.INVALID_UNIT_PRICE)
                val gross = line.gross.nonNegativeDecimalOrNull()
                    ?: return failure(SalesExportValidationCode.INVALID_GROSS)
                val discount = line.discount.nonNegativeDecimalOrNull()
                    ?: return failure(SalesExportValidationCode.INVALID_DISCOUNT)
                val net = line.net.nonNegativeDecimalOrNull()
                    ?: return failure(SalesExportValidationCode.INVALID_NET)
                if (line.commercialRevision < 0) return failure(SalesExportValidationCode.NEGATIVE_COMMERCIAL_REVISION)
                if (line.consumptionRevision < 0) return failure(SalesExportValidationCode.NEGATIVE_CONSUMPTION_REVISION)
                if (discount > gross) return failure(SalesExportValidationCode.DISCOUNT_EXCEEDS_GROSS)
                if (gross.compareTo(quantity.multiply(unitPrice)) != 0) return failure(SalesExportValidationCode.GROSS_MISMATCH)
                if (net.compareTo(gross.subtract(discount)) != 0) return failure(SalesExportValidationCode.NET_MISMATCH)
                if (!lineIds.add(line.saleLineId)) return failure(SalesExportValidationCode.DUPLICATE_SALE_LINE_ID)
            }
        }
        return null
    }

    private fun failure(code: SalesExportValidationCode) = SalesExportValidationFailure(code)
    private fun String.instantOrNull() = runCatching { Instant.parse(this) }.getOrNull()
    private fun String.decimalOrNull() = runCatching { BigDecimal(this) }.getOrNull()
    private fun String.nonNegativeDecimalOrNull() = decimalOrNull()?.takeIf { it >= BigDecimal.ZERO }
}

