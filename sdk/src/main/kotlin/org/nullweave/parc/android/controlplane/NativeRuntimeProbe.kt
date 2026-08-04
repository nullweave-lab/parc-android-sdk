package org.nullweave.parc.android.controlplane

import org.json.JSONArray
import org.json.JSONObject

public object NativeRuntimeProbe {
    private val loadFailure: Throwable? = runCatching {
        System.loadLibrary("parc_probe_jni")
    }.exceptionOrNull()

    private external fun collectJson(challenge: ByteArray): String

    public fun collect(challenge: ByteArray): Map<String, Any?> {
        loadFailure?.let { error ->
            return linkedMapOf(
                "status" to "unsupported",
                "error" to error.javaClass.simpleName,
                "limitations" to listOf("native-library-load-failed"),
            )
        }
        return runCatching { JSONObject(collectJson(challenge)).toMap() }
            .getOrElse { error ->
                linkedMapOf(
                    "status" to "failed",
                    "error" to error.javaClass.simpleName,
                    "limitations" to listOf("native-probe-execution-failed"),
                )
            }
    }

    private fun JSONObject.toMap(): Map<String, Any?> = buildMap {
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            put(key, convert(get(key)))
        }
    }

    private fun JSONArray.toListValue(): List<Any?> = buildList {
        for (index in 0 until length()) add(convert(get(index)))
    }

    private fun convert(value: Any?): Any? = when (value) {
        JSONObject.NULL -> null
        is JSONObject -> value.toMap()
        is JSONArray -> value.toListValue()
        else -> value
    }
}
