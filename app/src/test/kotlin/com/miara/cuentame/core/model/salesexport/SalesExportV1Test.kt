package com.miara.cuentame.core.model.salesexport

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SalesExportV1Test {
    @Test fun `golden fixture strictly decodes and encoding is deterministic`() {
        val golden = golden()
        val decoded = SalesExportJsonCodec.decodeAndValidate(golden)
        assertThat(decoded).isInstanceOf(SalesExportDecodeResult.Success::class.java)
        val value = (decoded as SalesExportDecodeResult.Success).value
        assertThat(SalesExportJsonCodec.encode(value)).isEqualTo(golden)
        assertThat(SalesExportJsonCodec.encode(value).toByteArray()).isEqualTo(SalesExportJsonCodec.encode(value).toByteArray())
        assertThat(value.transactions.map { it.status }).containsExactly("COMPLETED", "VOIDED").inOrder()
        assertThat(value.transactions.flatMap { it.lines }.map { it.sellableItemId }).containsExactly("burger", "wings", "burger").inOrder()
    }

    @Test fun `zero revisions void amounts repeated items and equivalent decimal scales are valid`() {
        val first = valid().transactions.first().lines.first().copy(
            quantity = "2.0", unitPrice = "13.00", gross = "26.000", discount = "0.780", net = "25.2200",
            commercialRevision = 0, consumptionRevision = 0
        )
        val transaction = valid().transactions.first().copy(status = "VOIDED", lines = listOf(first, first.copy(saleLineId = "line-other")))
        assertThat(SalesExportValidator.validate(valid().copy(transactions = listOf(transaction)))).isNull()
    }

    @Test fun `validator rejects envelope failures deterministically`() {
        val value = valid()
        val cases = listOf(
            value.copy(format = "wrong") to SalesExportValidationCode.WRONG_FORMAT,
            value.copy(formatVersion = 2) to SalesExportValidationCode.UNSUPPORTED_VERSION,
            value.copy(exportId = " ") to SalesExportValidationCode.BLANK_EXPORT_ID,
            value.copy(terminalId = "") to SalesExportValidationCode.BLANK_TERMINAL_ID,
            value.copy(restaurantId = "") to SalesExportValidationCode.BLANK_RESTAURANT_ID,
            value.copy(generatedAt = "today") to SalesExportValidationCode.INVALID_GENERATED_AT,
            value.copy(businessDate = "08/16/2026") to SalesExportValidationCode.INVALID_BUSINESS_DATE,
            value.copy(menuPackageId = "") to SalesExportValidationCode.BLANK_MENU_PACKAGE_ID,
            value.copy(menuId = "") to SalesExportValidationCode.BLANK_MENU_ID,
            value.copy(publicationRevision = 0) to SalesExportValidationCode.INVALID_PUBLICATION_REVISION,
            value.copy(currency = "NOPE") to SalesExportValidationCode.INVALID_CURRENCY,
            value.copy(transactions = emptyList()) to SalesExportValidationCode.NO_TRANSACTIONS
        )
        assertCases(cases)
    }

    @Test fun `validator rejects transaction failures deterministically`() {
        val value = valid(); val transaction = value.transactions.first()
        val cases = listOf(
            withTransaction(value, transaction.copy(transactionId = " ")) to SalesExportValidationCode.BLANK_TRANSACTION_ID,
            withTransaction(value, transaction.copy(openedAt = "bad")) to SalesExportValidationCode.INVALID_OPENED_AT,
            withTransaction(value, transaction.copy(closedAt = "bad")) to SalesExportValidationCode.INVALID_CLOSED_AT,
            withTransaction(value, transaction.copy(closedAt = "2026-08-16T18:39:59Z")) to SalesExportValidationCode.CLOSED_BEFORE_OPENED,
            withTransaction(value, transaction.copy(status = "OPEN")) to SalesExportValidationCode.INVALID_TRANSACTION_STATUS,
            withTransaction(value, transaction.copy(lines = emptyList())) to SalesExportValidationCode.NO_LINES,
            value.copy(transactions = listOf(transaction, transaction)) to SalesExportValidationCode.DUPLICATE_TRANSACTION_ID
        )
        assertCases(cases)
    }

    @Test fun `validator rejects line failures deterministically`() {
        val value = valid(); val line = value.transactions.first().lines.first()
        val cases = listOf(
            withLine(value, line.copy(saleLineId = " ")) to SalesExportValidationCode.BLANK_SALE_LINE_ID,
            withLine(value, line.copy(sellableItemId = "")) to SalesExportValidationCode.BLANK_SELLABLE_ITEM_ID,
            withLine(value, line.copy(displayNameSnapshot = "")) to SalesExportValidationCode.BLANK_ITEM_NAME,
            withLine(value, line.copy(quantity = "0")) to SalesExportValidationCode.INVALID_QUANTITY,
            withLine(value, line.copy(quantity = "-1")) to SalesExportValidationCode.INVALID_QUANTITY,
            withLine(value, line.copy(unitPrice = "-1")) to SalesExportValidationCode.INVALID_UNIT_PRICE,
            withLine(value, line.copy(gross = "-1")) to SalesExportValidationCode.INVALID_GROSS,
            withLine(value, line.copy(discount = "-1")) to SalesExportValidationCode.INVALID_DISCOUNT,
            withLine(value, line.copy(net = "-1")) to SalesExportValidationCode.INVALID_NET,
            withLine(value, line.copy(discount = "27", net = "0")) to SalesExportValidationCode.DISCOUNT_EXCEEDS_GROSS,
            withLine(value, line.copy(gross = "25")) to SalesExportValidationCode.GROSS_MISMATCH,
            withLine(value, line.copy(net = "25")) to SalesExportValidationCode.NET_MISMATCH,
            withLine(value, line.copy(commercialRevision = -1)) to SalesExportValidationCode.NEGATIVE_COMMERCIAL_REVISION,
            withLine(value, line.copy(consumptionRevision = -1)) to SalesExportValidationCode.NEGATIVE_CONSUMPTION_REVISION,
            withTransaction(value, value.transactions.first().copy(lines = listOf(line, line))) to SalesExportValidationCode.DUPLICATE_SALE_LINE_ID
        )
        assertCases(cases)
    }

    @Test fun `strict codec rejects malformed json unknown keys wrong types and invalid status`() {
        val json = golden()
        assertThat(SalesExportJsonCodec.decodeAndValidate("{")).isInstanceOf(SalesExportDecodeResult.InvalidJson::class.java)
        assertThat(SalesExportJsonCodec.decodeAndValidate(json.replaceFirst("{", "{\n  \"extra\": true,"))).isInstanceOf(SalesExportDecodeResult.InvalidJson::class.java)
        assertThat(SalesExportJsonCodec.decodeAndValidate(json.replace("\"formatVersion\": 1", "\"formatVersion\": \"1\""))).isInstanceOf(SalesExportDecodeResult.InvalidJson::class.java)
        assertThat(SalesExportJsonCodec.decodeAndValidate(json.replace("\"quantity\": \"2\"", "\"quantity\": 2"))).isInstanceOf(SalesExportDecodeResult.InvalidJson::class.java)
        assertInvalidExport(json.replace("\"status\": \"COMPLETED\"", "\"status\": \"OPEN\""), SalesExportValidationCode.INVALID_TRANSACTION_STATUS)
    }

    private fun assertCases(cases: List<Pair<SalesExportV1, SalesExportValidationCode>>) = cases.forEach { (value, code) ->
        assertThat(SalesExportValidator.validate(value)?.code).isEqualTo(code)
    }

    private fun assertInvalidExport(json: String, code: SalesExportValidationCode) {
        val result = SalesExportJsonCodec.decodeAndValidate(json)
        assertThat(result).isInstanceOf(SalesExportDecodeResult.InvalidExport::class.java)
        assertThat((result as SalesExportDecodeResult.InvalidExport).failure.code).isEqualTo(code)
    }

    private fun withTransaction(value: SalesExportV1, transaction: SalesExportTransactionV1) = value.copy(transactions = listOf(transaction))
    private fun withLine(value: SalesExportV1, line: SalesExportLineV1) =
        withTransaction(value, value.transactions.first().copy(lines = listOf(line)))

    private fun golden() = requireNotNull(javaClass.getResource("/salesexport/sales-export-v1-golden.json")).readText().trimEnd()
    private fun valid() = (SalesExportJsonCodec.decodeAndValidate(golden()) as SalesExportDecodeResult.Success).value
}
