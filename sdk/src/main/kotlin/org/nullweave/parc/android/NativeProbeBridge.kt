package org.nullweave.parc.android

/**
 * Optional boundary for an artifact produced by `parc-native-probes`.
 *
 * The SDK does not load a native library automatically. Integrators must make
 * the library identity, supported ABI set, version, and loading failure visible.
 */
public interface NativeProbeBridge {
    public val implementationVersion: String

    public val supportedAbi: String

    public suspend fun collect(probeName: String, challenge: ByteArray): NativeProbeResult
}

public data class NativeProbeResult(
    public val status: EvidenceStatus,
    public val payload: ByteArray,
    public val limitations: List<String> = emptyList(),
) {
    override fun equals(other: Any?): Boolean =
        other is NativeProbeResult &&
            status == other.status &&
            payload.contentEquals(other.payload) &&
            limitations == other.limitations

    override fun hashCode(): Int {
        var result = status.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + limitations.hashCode()
        return result
    }
}
