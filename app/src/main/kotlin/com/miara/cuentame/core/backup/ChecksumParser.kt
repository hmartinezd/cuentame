package com.miara.cuentame.core.backup

import kotlinx.serialization.json.*

object ChecksumParser {

    /**
     * Strictly parses a checksums.json payload.
     * Rejects duplicates, non-string values, and malformed SHA-256 hashes.
     */
    fun parse(jsonContent: String): Result<Map<String, String>> {
        if (jsonContent.isBlank()) return Result.failure(Exception("Empty checksums.json"))
        
        try {
            val element = Json.parseToJsonElement(jsonContent)
            if (element !is JsonObject) return Result.failure(Exception("checksums.json must be a single JSON object"))

            // Manual duplicate key detection in raw string because parseToJsonElement omits them
            val keysFound = mutableSetOf<String>()
            val keyRegex = Regex("\"([^\"]+)\"\\s*:")
            val matches = keyRegex.findAll(jsonContent)
            for (match in matches) {
                val key = match.groupValues[1]
                val decodedKey = try { 
                    Json.decodeFromString<String>("\"$key\"") 
                } catch (e: Exception) {
                    return Result.failure(Exception("Malformed key escape: $key"))
                }
                if (!keysFound.add(decodedKey)) {
                    return Result.failure(Exception("Duplicate key in checksums.json: $decodedKey"))
                }
            }

            val result = mutableMapOf<String, String>()
            for ((key, value) in element) {
                if (value !is JsonPrimitive || !value.isString) {
                    return Result.failure(Exception("Checksum for $key must be a string"))
                }
                val hash = value.content
                if (!hash.matches(Regex("^[a-f0-9]{64}$"))) {
                    return Result.failure(Exception("Invalid SHA-256 hash format for $key: $hash"))
                }
                result[key] = hash
            }

            if (result.size != keysFound.size) {
                return Result.failure(Exception("Duplicate keys detected in checksums.json"))
            }

            if (result.isEmpty()) return Result.failure(Exception("checksums.json cannot be empty"))

            return Result.success(result)
        } catch (e: Exception) {
            return Result.failure(Exception("Malformed checksums.json: ${e.message}"))
        }
    }
}
