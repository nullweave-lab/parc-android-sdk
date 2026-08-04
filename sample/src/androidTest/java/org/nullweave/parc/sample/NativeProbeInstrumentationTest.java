package org.nullweave.parc.sample;

import android.test.InstrumentationTestCase;

import org.nullweave.parc.android.controlplane.NativeRuntimeProbe;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class NativeProbeInstrumentationTest extends InstrumentationTestCase {
    public void testJniProbeLoadsAndReturnsBoundedProcessSummary() {
        byte[] challenge = "instrumentation-challenge".getBytes(StandardCharsets.UTF_8);
        Map<String, ?> result = NativeRuntimeProbe.INSTANCE.collect(challenge);

        assertFalse("native library did not load or execute: " + result, result.containsKey("status"));
        assertEquals("0.1.0", result.get("implementation_version"));
        assertEquals(challenge.length, ((Number) result.get("challenge_length")).intValue());
        assertTrue(result.get("challenge_tag_fnv1a64") instanceof String);
        assertEquals(16, ((String) result.get("challenge_tag_fnv1a64")).length());

        Object observations = result.get("observations");
        if (observations instanceof List) {
            assertTrue("expected maps, mountinfo, and status observations", ((List<?>) observations).size() >= 3);
        } else {
            assertTrue(result.get("maps_records") instanceof Number);
            assertTrue(result.get("mount_records") instanceof Number);
            assertTrue(result.get("tracer_pid") instanceof Number);
            assertTrue(result.get("seccomp") instanceof Number);
            assertTrue(result.get("maps_malformed_records") instanceof Number);
            assertTrue(result.get("mount_malformed_records") instanceof Number);
        }

        Object limitations = result.get("limitations");
        assertTrue(limitations instanceof List);
        assertFalse(((List<?>) limitations).isEmpty());
    }
}
