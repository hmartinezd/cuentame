package com.venkoi.restaurantops.core.model.salesexport

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

sealed class SalesExportDecodeResult {
    data class Success(val value: SalesExportV1) : SalesExportDecodeResult()
    data class InvalidJson(val cause: Throwable) : SalesExportDecodeResult()
    data class InvalidExport(val failure: SalesExportValidationFailure) : SalesExportDecodeResult()
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object SalesExportJsonCodec {
    private val json = Json {
        ignoreUnknownKeys = false
        coerceInputValues = false
        explicitNulls = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun encode(value: SalesExportV1): String {
        SalesExportValidator.validate(value)?.let { throw IllegalArgumentException("Invalid SalesExport: ${it.code}") }
        return json.encodeToString(value)
    }

    fun decodeAndValidate(text: String): SalesExportDecodeResult {
        return try {
            val element = json.parseToJsonElement(text)
            if (!hasStrictTypes(element)) return SalesExportDecodeResult.InvalidJson(IllegalArgumentException("Invalid SalesExport field type"))
            val value = json.decodeFromString<SalesExportV1>(text)
            SalesExportValidator.validate(value)?.let { SalesExportDecodeResult.InvalidExport(it) }
                ?: SalesExportDecodeResult.Success(value)
        } catch (e: SerializationException) {
            SalesExportDecodeResult.InvalidJson(e)
        } catch (e: IllegalArgumentException) {
            SalesExportDecodeResult.InvalidJson(e)
        }
    }

    private fun hasStrictTypes(element: JsonElement): Boolean = runCatching {
        val root = element.jsonObject
        root.string("format"); root.number("formatVersion"); root.string("exportId")
        root.string("terminalId"); root.string("restaurantId"); root.string("generatedAt")
        root.string("businessDate"); root.string("menuPackageId"); root.string("menuId")
        root.number("publicationRevision"); root.string("currency")
        root.getValue("transactions").jsonArray.forEach { transactionElement ->
            val transaction = transactionElement.jsonObject
            transaction.string("transactionId"); transaction.string("openedAt"); transaction.string("closedAt"); transaction.string("status")
            transaction.getValue("lines").jsonArray.forEach { lineElement ->
                val line = lineElement.jsonObject
                line.string("saleLineId"); line.string("sellableItemId"); line.string("displayNameSnapshot")
                line.string("quantity"); line.string("unitPrice"); line.string("gross"); line.string("discount"); line.string("net")
                line.number("commercialRevision"); line.number("consumptionRevision")
            }
        }
    }.isSuccess

    private fun JsonObject.string(key: String) { if (!getValue(key).jsonPrimitive.isString) error(key) }
    private fun JsonObject.number(key: String) { if (getValue(key).jsonPrimitive.isString) error(key) }
}
