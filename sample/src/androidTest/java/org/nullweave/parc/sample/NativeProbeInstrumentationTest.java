package org.nullweave.parc.sample;

import android.test.InstrumentationTestCase;
import android.util.Log;

import org.nullweave.parc.android.controlplane.NativeRuntimeProbe;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class NativeProbeInstrumentationTest extends InstrumentationTestCase {
    private static final String TAG = "PArCNativeProbeTest";

    public void testJniProbeLoadsAndReturnsChallengeBoundStructure() {
        byte[] challenge = "instrumentation-challenge".getBytes(StandardCharsets.UTF_8);
        Map<String, ?> result = NativeRuntimeProbe.INSTANCE.collect(challenge);
        Log.i(TAG, "native result=" + result);

        assertNotNull("native probe returned null", result);
        assertFalse("native library did not load or execute: " + result, result.isEmpty());
        assertFalse("native library reported failure: " + result, result.containsKey("status"));
        assertEquals("0.1.0", result.get("implementation_version"));

        Object challengeLength = result.get("challenge_length");
        assertTrue("missing numeric challenge length: " + result, challengeLength instanceof Number);
        assertEquals(challenge.length, ((Number) challengeLength).intValue());

        Object challengeTag = result.get("challenge_tag_fnv1a64");
        assertTrue("missing challenge correlation tag: " + result, challengeTag instanceof String);
        assertEquals(16, ((String) challengeTag).length());

        Object limitations = result.get("limitations");
        assertTrue("missing explicit limitations: " + result, limitations instanceof List);
        assertFalse("limitations must not be empty", ((List<?>) limitations).isEmpty());
    }
}
