package com.miara.cuentame.core.backup

object ChecksumParser {

    /**
     * Character-by-character parser for checksums.json.
     * Guarantees:
     * - Top-level JSON object containing string:string entries only.
     * - No duplicate keys (including via different escape sequences).
     * - Valid SHA-256 lower-case hex format for values.
     * - Explicit rejection of "checksums.json" key.
     * - Rejection of empty objects, nested structures, numbers, booleans, nulls, malformed escapes, trailing content.
     * - Redacted/sanitized error messages (no raw customer payload data).
     */
    fun parse(jsonContent: String): Result<Map<String, String>> {
        val s = jsonContent.trim()
        if (s.isEmpty()) return Result.failure(Exception("Checksums JSON is empty"))

        var pos = 0
        fun skipWhitespace() {
            while (pos < s.length && (s[pos] == ' ' || s[pos] == '\t' || s[pos] == '\r' || s[pos] == '\n')) {
                pos++
            }
        }

        fun parseString(): String {
            if (pos >= s.length || s[pos] != '"') throw Exception("Expected string starting with quote")
            pos++ // skip opening quote
            val sb = StringBuilder()
            while (pos < s.length) {
                val ch = s[pos++]
                if (ch == '"') {
                    return sb.toString()
                }
                if (ch == '\\') {
                    if (pos >= s.length) throw Exception("Unterminated escape sequence")
                    when (val esc = s[pos++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > s.length) throw Exception("Truncated unicode escape")
                            val hex = s.substring(pos, pos + 4)
                            val code = hex.toIntOrNull(16) ?: throw Exception("Invalid hex code in unicode escape")
                            sb.append(code.toChar())
                            pos += 4
                        }
                        else -> throw Exception("Invalid escape character")
                    }
                } else if (ch.code < 0x20) {
                    throw Exception("Unescaped control character in string")
                } else {
                    sb.append(ch)
                }
            }
            throw Exception("Unterminated string")
        }

        try {
            skipWhitespace()
            if (pos >= s.length || s[pos] != '{') {
                return Result.failure(Exception("Checksums JSON must be a single object"))
            }
            pos++ // skip '{'
            skipWhitespace()

            if (pos < s.length && s[pos] == '}') {
                return Result.failure(Exception("Checksums object cannot be empty"))
            }

            val keysSeen = mutableSetOf<String>()
            val result = mutableMapOf<String, String>()
            val shaRegex = Regex("^[a-f0-9]{64}$")

            while (pos < s.length) {
                skipWhitespace()
                if (pos >= s.length || s[pos] != '"') {
                    return Result.failure(Exception("Expected key string"))
                }

                val key = parseString()
                if (key == "checksums.json") {
                    return Result.failure(Exception("checksums.json self-referential key forbidden"))
                }

                if (!keysSeen.add(key)) {
                    return Result.failure(Exception("Duplicate key detected"))
                }

                skipWhitespace()
                if (pos >= s.length || s[pos] != ':') {
                    return Result.failure(Exception("Expected colon after key"))
                }
                pos++ // skip ':'
                skipWhitespace()

                if (pos >= s.length || s[pos] != '"') {
                    return Result.failure(Exception("Checksum value must be a string"))
                }

                val value = parseString()
                if (!shaRegex.matches(value)) {
                    return Result.failure(Exception("Invalid SHA-256 hash format"))
                }

                result[key] = value

                skipWhitespace()
                if (pos < s.length && s[pos] == ',') {
                    pos++ // skip ','
                    skipWhitespace()
                    if (pos < s.length && s[pos] == '}') {
                        return Result.failure(Exception("Trailing comma before closing brace"))
                    }
                } else if (pos < s.length && s[pos] == '}') {
                    pos++ // skip '}'
                    break
                } else {
                    return Result.failure(Exception("Expected comma or closing brace"))
                }
            }

            skipWhitespace()
            if (pos < s.length) {
                return Result.failure(Exception("Unexpected trailing content"))
            }

            return Result.success(result)
        } catch (e: Exception) {
            return Result.failure(Exception("Checksum parsing failed: ${e.message}"))
        }
    }
}
