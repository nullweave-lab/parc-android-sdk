package org.nullweave.parc.android.controlplane

import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

public object ProofCodec {
    public fun canonicalJson(value: Any?): String = when (value) {
        null -> "null"
        is Boolean, is Byte, is Short, is Int, is Long, is Float, is Double -> value.toString()
        is String -> quote(value)
        is Map<*, *> -> value.entries
            .map { (key, child) -> require(key is String) { "JSON object keys must be strings" }; key to child }
            .sortedBy { it.first }
            .joinToString(prefix = "{", postfix = "}") { (key, child) -> "${quote(key)}:${canonicalJson(child)}" }
        is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { canonicalJson(it) }
        is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { canonicalJson(it) }
        else -> throw IllegalArgumentException("unsupported canonical JSON value: ${value::class.java.name}")
    }

    public fun calculateMac(
        deviceSecret: String,
        challengeId: String,
        nonce: String,
        deviceId: String,
        evidence: Map<String, Any?>,
    ): String {
        val key = Base64.decode(deviceSecret, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val message = "$challengeId.$nonce.$deviceId.${canonicalJson(evidence)}"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(message.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun quote(value: String): String = buildString(value.length + 2) {
        append('"')
        for (character in value) {
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
            }
        }
        append('"')
    }
}
