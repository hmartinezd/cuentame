package com.venkoi.restaurantops.core.cloud.sync

import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.database.sync.InventoryAreaRemoteApplyResult
import com.venkoi.restaurantops.core.database.sync.InventoryAreaRemoteOperation
import com.venkoi.restaurantops.core.database.sync.InventoryAreaSyncPayload
import com.venkoi.restaurantops.core.database.sync.InventoryAreaSyncProtocolException
import com.venkoi.restaurantops.core.database.sync.InventoryAreaSyncRemoteDataSource
import com.venkoi.restaurantops.core.database.sync.RemoteInventoryArea
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class SupabaseInventoryAreaSyncRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
    private val json: Json
) : InventoryAreaSyncRemoteDataSource {
    override suspend fun apply(operation: InventoryAreaRemoteOperation): Result<InventoryAreaRemoteApplyResult> =
        cloudOperation {
            val payload = try {
                json.decodeFromString<InventoryAreaSyncPayload>(operation.payloadJson)
            } catch (_: Exception) {
                throw InventoryAreaSyncProtocolException()
            }
            if (payload.id != operation.entityId || payload.restaurantId != operation.restaurantId) {
                throw InventoryAreaSyncProtocolException()
            }
            val parameters = buildApplyParameters(operation, payload)
            supabase.postgrest.rpc("apply_inventory_area_sync", parameters)
                .decodeSingle<InventoryAreaApplyRpcDto>()
                .toRemoteResult()
        }

    override suspend fun pull(
        restaurantId: RestaurantId,
        afterChangeSeq: Long,
        limit: Int
    ): Result<List<RemoteInventoryArea>> = cloudOperation {
        supabase.from("inventory_areas").select {
            filter {
                eq("restaurant_id", restaurantId.value)
                gt("change_seq", afterChangeSeq)
            }
            order("change_seq", Order.ASCENDING)
            limit(limit.toLong())
        }.decodeList<RemoteInventoryAreaDto>().map { it.toRemote() }
    }
}

internal fun epochMillisToIso(value: Long): String = Instant.ofEpochMilli(value).toString()
internal fun isoToEpochMillis(value: String): Long = Instant.parse(value).toEpochMilli()

internal fun buildApplyParameters(
    operation: InventoryAreaRemoteOperation,
    payload: InventoryAreaSyncPayload
) = buildJsonObject {
    put("p_operation_id", operation.operationId)
    put("p_restaurant_id", operation.restaurantId)
    put("p_entity_id", operation.entityId)
    put("p_base_server_version", operation.baseServerVersion)
    put("p_name", payload.name)
    put("p_normalized_name", payload.normalizedName)
    put("p_sort_order", payload.sortOrder)
    put("p_is_active", payload.isActive)
    put("p_created_at", epochMillisToIso(payload.createdAt))
    put("p_updated_at", epochMillisToIso(payload.updatedAt))
    if (payload.deletedAt == null) {
        put("p_deleted_at", kotlinx.serialization.json.JsonNull)
    } else {
        put("p_deleted_at", epochMillisToIso(payload.deletedAt))
    }
}

private suspend inline fun <T> cloudOperation(crossinline block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (protocol: InventoryAreaSyncProtocolException) {
    Result.failure(protocol)
} catch (_: Exception) {
    Result.failure(InventoryAreaSyncRemoteException())
}

class InventoryAreaSyncRemoteException : Exception()

@Serializable
internal data class InventoryAreaApplyRpcDto(
    val status: String,
    @SerialName("entity_id") val entityId: String,
    @SerialName("server_version") val serverVersion: Long? = null,
    @SerialName("change_seq") val changeSeq: Long? = null,
    @SerialName("current_server_version") val currentServerVersion: Long? = null,
    @SerialName("current_change_seq") val currentChangeSeq: Long? = null
)

internal fun InventoryAreaApplyRpcDto.toRemoteResult(): InventoryAreaRemoteApplyResult = when (status) {
    "APPLIED" -> InventoryAreaRemoteApplyResult.Applied(
        entityId, serverVersion ?: throw InventoryAreaSyncProtocolException(),
        changeSeq ?: throw InventoryAreaSyncProtocolException()
    )
    "ALREADY_APPLIED" -> InventoryAreaRemoteApplyResult.AlreadyApplied(
        entityId, serverVersion ?: throw InventoryAreaSyncProtocolException(),
        changeSeq ?: throw InventoryAreaSyncProtocolException()
    )
    "CONFLICT" -> InventoryAreaRemoteApplyResult.Conflict(
        entityId, currentServerVersion ?: throw InventoryAreaSyncProtocolException(), currentChangeSeq
    )
    "INVALID_OPERATION" -> InventoryAreaRemoteApplyResult.InvalidOperation(entityId)
    else -> throw InventoryAreaSyncProtocolException()
}

@Serializable
internal data class RemoteInventoryAreaDto(
    val id: String,
    @SerialName("restaurant_id") val restaurantId: String,
    val name: String,
    @SerialName("normalized_name") val normalizedName: String,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("server_version") val serverVersion: Long,
    @SerialName("change_seq") val changeSeq: Long
)

internal fun RemoteInventoryAreaDto.toRemote() = try {
    RemoteInventoryArea(
        id, restaurantId, name, normalizedName, sortOrder, isActive,
        isoToEpochMillis(createdAt), isoToEpochMillis(updatedAt), deletedAt?.let(::isoToEpochMillis),
        serverVersion, changeSeq
    )
} catch (_: Exception) {
    throw InventoryAreaSyncProtocolException()
}
