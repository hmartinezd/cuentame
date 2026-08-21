package com.venkoi.restaurantops.core.cloud.sync

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.database.sync.InventoryAreaRemoteApplyResult
import com.venkoi.restaurantops.core.database.sync.InventoryAreaRemoteOperation
import com.venkoi.restaurantops.core.database.sync.InventoryAreaSyncPayload
import com.venkoi.restaurantops.core.database.sync.InventoryAreaSyncProtocolException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class InventoryAreaSyncCloudMappingTest {
    @Test fun `maps all accepted RPC statuses`() {
        assertThat(dto("APPLIED", 2, 3).toRemoteResult())
            .isEqualTo(InventoryAreaRemoteApplyResult.Applied("area", 2, 3))
        assertThat(dto("ALREADY_APPLIED", 2, 3).toRemoteResult())
            .isEqualTo(InventoryAreaRemoteApplyResult.AlreadyApplied("area", 2, 3))
        assertThat(InventoryAreaApplyRpcDto("CONFLICT", "area", currentServerVersion = 5, currentChangeSeq = 8).toRemoteResult())
            .isEqualTo(InventoryAreaRemoteApplyResult.Conflict("area", 5, 8))
        assertThat(InventoryAreaApplyRpcDto("INVALID_OPERATION", "area").toRemoteResult())
            .isEqualTo(InventoryAreaRemoteApplyResult.InvalidOperation("area"))
    }

    @Test fun `unknown or incomplete status is protocol failure`() {
        assertThat(runCatching { dto("NEW_STATUS", 1, 1).toRemoteResult() }.exceptionOrNull())
            .isInstanceOf(InventoryAreaSyncProtocolException::class.java)
        assertThat(runCatching { InventoryAreaApplyRpcDto("APPLIED", "area").toRemoteResult() }.exceptionOrNull())
            .isInstanceOf(InventoryAreaSyncProtocolException::class.java)
    }

    @Test fun `timestamps use UTC ISO and decode explicitly to epoch millis`() {
        assertThat(epochMillisToIso(0)).isEqualTo("1970-01-01T00:00:00Z")
        assertThat(isoToEpochMillis("2026-08-21T18:00:00-04:00")).isEqualTo(1787349600000L)
        val remote = RemoteInventoryAreaDto(
            "area", "restaurant", "Area", "area", 1, true,
            "1970-01-01T00:00:00Z", "1970-01-01T00:00:01Z", null, 2, 3
        ).toRemote()
        assertThat(remote.createdAt).isEqualTo(0)
        assertThat(remote.updatedAt).isEqualTo(1000)
    }

    @Test fun `RPC parameters use accepted names immutable identity payload and ISO timestamps`() {
        val operation = InventoryAreaRemoteOperation("operation", "restaurant", "area", 7, "immutable-json")
        val payload = InventoryAreaSyncPayload("area", "restaurant", "Kitchen", "kitchen", 4, false, 0, 1000, 2000)

        val parameters = buildApplyParameters(operation, payload)

        assertThat(parameters.keys).containsExactly(
            "p_operation_id", "p_restaurant_id", "p_entity_id", "p_base_server_version",
            "p_name", "p_normalized_name", "p_sort_order", "p_is_active",
            "p_created_at", "p_updated_at", "p_deleted_at"
        )
        assertThat(parameters.getValue("p_operation_id").jsonPrimitive.content).isEqualTo("operation")
        assertThat(parameters.getValue("p_base_server_version").jsonPrimitive.content).isEqualTo("7")
        assertThat(parameters.getValue("p_created_at").jsonPrimitive.content).isEqualTo("1970-01-01T00:00:00Z")
        assertThat(parameters.getValue("p_deleted_at").jsonPrimitive.content).isEqualTo("1970-01-01T00:00:02Z")
    }

    @Test fun `remote SELECT DTO decodes snake case fields`() {
        val dto = Json.decodeFromString<RemoteInventoryAreaDto>("""
            {"id":"a","restaurant_id":"r","name":"A","normalized_name":"a",
             "sort_order":2,"is_active":true,"created_at":"1970-01-01T00:00:00Z",
             "updated_at":"1970-01-01T00:00:01Z","deleted_at":null,
             "server_version":3,"change_seq":4}
        """.trimIndent())

        assertThat(dto.restaurantId).isEqualTo("r")
        assertThat(dto.normalizedName).isEqualTo("a")
        assertThat(dto.serverVersion).isEqualTo(3)
        assertThat(dto.changeSeq).isEqualTo(4)
    }

    private fun dto(status: String, version: Long, seq: Long) =
        InventoryAreaApplyRpcDto(status, "area", version, seq, version, seq)
}
