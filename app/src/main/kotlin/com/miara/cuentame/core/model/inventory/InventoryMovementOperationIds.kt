package com.miara.cuentame.core.model.inventory

object InventoryMovementOperationIds {

    private fun part(value: String): String = "${value.length}:$value"

    fun salesConsumption(saleLineId: String, publicationComponentId: String): String =
        "sales-consumption:${part(saleLineId)}:${part(publicationComponentId)}"

    fun purchasePost(
        receiptId: String,
        lineId: String
    ): String =
        "purchase-post:$receiptId:$lineId"

    fun wastePost(
        eventId: String
    ): String =
        "waste-post:$eventId"

    fun productionConsumption(
        batchId: String,
        componentId: String
    ): String =
        "production-post:$batchId:consume:$componentId"

    fun productionOutput(
        batchId: String
    ): String =
        "production-post:$batchId:output"

    fun reversal(
        originalMovementId: String
    ): String =
        "reversal:$originalMovementId"
}
