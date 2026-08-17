package com.miara.cuentame.core.model.salesimport

/** Stable, collision-safe ledger identity for the canonical (terminal, transaction) key. */
object SalesTransactionSourceIdentity {
    private fun part(value: String): String = "${value.length}:$value"

    fun encode(terminalId: String, transactionId: String): String =
        "sales-tx:${part(terminalId)}:${part(transactionId)}"
}
