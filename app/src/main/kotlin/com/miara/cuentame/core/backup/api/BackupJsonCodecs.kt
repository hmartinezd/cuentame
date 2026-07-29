package com.miara.cuentame.core.backup.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupJsonCodecs @Inject constructor() {

    val writer: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        @OptIn(ExperimentalSerializationApi::class)
        explicitNulls = true
    }

    val reader: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        @OptIn(ExperimentalSerializationApi::class)
        allowTrailingComma = false
        @OptIn(ExperimentalSerializationApi::class)
        explicitNulls = true
    }
}
