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

        assertEquals("0.1.0", result.get("implementation_version"));
        assertEquals(challenge.length, ((Number) result.get("challenge_length")).intValue());
        assertTrue(((Number) result.get("maps_records")).intValue() > 0);
        assertTrue(((Number) result.get("mount_records")).intValue() > 0);
        assertTrue(result.containsKey("tracer_pid"));
        assertTrue(result.containsKey("seccomp"));

        Object limitations = result.get("limitations");
        assertTrue(limitations instanceof List);
        assertFalse(((List<?>) limitations).isEmpty());
    }
}
