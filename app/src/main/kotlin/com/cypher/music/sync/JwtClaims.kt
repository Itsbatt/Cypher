package com.cypher.music.sync

import org.json.JSONObject
import java.util.Base64

/**
 * Reads the claims Cypher needs from a Google ID token, without an extra dependency.
 *
 * The token is only parsed, never validated here: signature validation belongs to the
 * backend (and to Google). Locally this is used for the email, the account id and the
 * name shown in Settings.
 */
object JwtClaims {

    fun email(idToken: String?): String? = claim(idToken, "email")

    fun subject(idToken: String?): String? = claim(idToken, "sub")

    fun claim(idToken: String?, name: String): String? {
        val parts = idToken?.trim()?.split('.') ?: return null
        if (parts.size < 2) return null
        val payload = decodeBase64Url(parts[1]) ?: return null
        return runCatching { JSONObject(payload).optString(name).takeIf { it.isNotBlank() } }.getOrNull()
    }

    /** JWT uses base64url without padding; java.util.Base64 keeps this unit testable. */
    internal fun decodeBase64Url(raw: String): String? = runCatching {
        val normalized = raw.replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
    }.getOrNull()
}
