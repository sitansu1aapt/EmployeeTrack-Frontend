package com.yatri

import org.json.JSONObject

/** Decodes JWT payload (no signature verification; for display / client IDs only). */
object JwtPayload {
    fun decodePayload(token: String): JSONObject? {
        val parts = token.split(".")
        if (parts.size < 2) return null
        val payload = parts[1]
            .replace('-', '+')
            .replace('_', '/')
        val padded = payload + when (payload.length % 4) {
            2 -> "=="
            3 -> "="
            else -> ""
        }
        return try {
            val decoded = android.util.Base64.decode(padded, android.util.Base64.DEFAULT)
            JSONObject(String(decoded, Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    fun userId(token: String): String? =
        decodePayload(token)?.let { j ->
            j.optString("userId").takeIf { it.isNotEmpty() }
                ?: j.optString("sub").takeIf { it.isNotEmpty() }
        }
}
