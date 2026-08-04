package org.nullweave.parc.android.controlplane

import kotlin.test.Test
import kotlin.test.assertEquals

class ProofCodecTest {
    @Test
    fun canonicalJsonSortsObjectKeys() {
        assertEquals(
            "{\"a\":true,\"b\":2,\"nested\":{\"x\":\"v\",\"z\":null}}",
            ProofCodec.canonicalJson(
                linkedMapOf(
                    "b" to 2,
                    "nested" to linkedMapOf("z" to null, "x" to "v"),
                    "a" to true,
                ),
            ),
        )
    }
}
