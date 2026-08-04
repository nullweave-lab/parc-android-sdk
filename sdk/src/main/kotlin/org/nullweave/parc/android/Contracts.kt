package org.nullweave.parc.android

public fun interface ChallengeProvider {
    public suspend fun requestChallenge(profile: String): Challenge
}

public interface EvidenceProvider {
    public val capabilityName: String

    public suspend fun capability(): Capability

    public suspend fun collect(context: CollectionContext): EvidenceRecord
}

public data class CollectionContext(
    public val challenge: Challenge,
    public val startedAtEpochMillis: Long,
)

public fun interface ProofEncoder {
    public fun encode(input: ProofInput): UnsignedProof
}

public data class ProofInput(
    public val challenge: Challenge,
    public val proofId: String,
    public val startedAtEpochMillis: Long,
    public val completedAtEpochMillis: Long,
    public val capabilities: List<Capability>,
    public val evidence: List<EvidenceRecord>,
)

public fun interface ProofSigner {
    public suspend fun sign(proof: UnsignedProof): SignedProof
}

public fun interface ProofTransport {
    public suspend fun submit(proof: SignedProof): VerificationResult
}

public fun interface Clock {
    public fun nowEpochMillis(): Long
}

public fun interface ProofIdGenerator {
    public fun newProofId(): String
}
