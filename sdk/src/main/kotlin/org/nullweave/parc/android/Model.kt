package org.nullweave.parc.android

/** Verifier-issued challenge and requested protocol profile. */
public data class Challenge(
    public val value: ByteArray,
    public val protocolVersion: String,
    public val profile: String,
    public val expiresAtEpochMillis: Long,
) {
    override fun equals(other: Any?): Boolean =
        other is Challenge &&
            value.contentEquals(other.value) &&
            protocolVersion == other.protocolVersion &&
            profile == other.profile &&
            expiresAtEpochMillis == other.expiresAtEpochMillis

    override fun hashCode(): Int {
        var result = value.contentHashCode()
        result = 31 * result + protocolVersion.hashCode()
        result = 31 * result + profile.hashCode()
        result = 31 * result + expiresAtEpochMillis.hashCode()
        return result
    }
}

public enum class CapabilityStatus {
    AVAILABLE,
    UNSUPPORTED,
    FAILED,
    WITHHELD,
}

public data class Capability(
    public val name: String,
    public val status: CapabilityStatus,
    public val reason: String? = null,
)

public enum class EvidenceStatus {
    PRESENT,
    UNSUPPORTED,
    FAILED,
    INDETERMINATE,
    WITHHELD,
}

public data class EvidenceProducer(
    public val id: String,
    public val boundary: String,
    public val mediation: String? = null,
)

public data class EvidenceRecord(
    public val id: String,
    public val kind: String,
    public val producer: EvidenceProducer,
    public val status: EvidenceStatus,
    public val claims: Map<String, String> = emptyMap(),
    public val dependencies: List<String> = emptyList(),
    public val limitations: List<String> = emptyList(),
)

public data class UnsignedProof(
    public val bytes: ByteArray,
    public val mediaType: String,
) {
    override fun equals(other: Any?): Boolean =
        other is UnsignedProof && bytes.contentEquals(other.bytes) && mediaType == other.mediaType

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + mediaType.hashCode()
}

public data class SignedProof(
    public val bytes: ByteArray,
    public val mediaType: String,
) {
    override fun equals(other: Any?): Boolean =
        other is SignedProof && bytes.contentEquals(other.bytes) && mediaType == other.mediaType

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + mediaType.hashCode()
}

public enum class VerificationStatus {
    PASS,
    FAIL,
    INDETERMINATE,
    UNSUPPORTED,
    MALFORMED,
    STALE,
    REPLAYED,
}

public data class VerificationResult(
    public val status: VerificationStatus,
    public val reasons: List<String>,
    public val policyVersion: String? = null,
)
