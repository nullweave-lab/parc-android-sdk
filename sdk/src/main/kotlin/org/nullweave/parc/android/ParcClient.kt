package org.nullweave.parc.android

public class ParcClient(
    private val challengeProvider: ChallengeProvider,
    private val evidenceProviders: List<EvidenceProvider>,
    private val proofEncoder: ProofEncoder,
    private val proofSigner: ProofSigner,
    private val proofTransport: ProofTransport,
    private val clock: Clock,
    private val proofIdGenerator: ProofIdGenerator,
) {
    public suspend fun attest(profile: String): VerificationResult {
        require(profile.isNotBlank()) { "profile must not be blank" }

        val suppliedChallenge = challengeProvider.requestChallenge(profile)
        val challenge = suppliedChallenge.copy(value = suppliedChallenge.value.copyOf())
        validateChallenge(challenge, profile)

        val startedAt = clock.nowEpochMillis()
        val capabilities = ArrayList<Capability>(evidenceProviders.size)
        val evidence = ArrayList<EvidenceRecord>(evidenceProviders.size)

        for (provider in evidenceProviders) {
            val capability = runCatching { provider.capability() }
                .getOrElse {
                    Capability(
                        name = provider.capabilityName,
                        status = CapabilityStatus.FAILED,
                        reason = it::class.java.simpleName,
                    )
                }
            capabilities += capability

            when (capability.status) {
                CapabilityStatus.AVAILABLE -> {
                    val providerContext = CollectionContext(
                        challenge = challenge.copy(value = challenge.value.copyOf()),
                        startedAtEpochMillis = startedAt,
                    )
                    evidence += runCatching { provider.collect(providerContext) }
                        .getOrElse {
                            EvidenceRecord(
                                id = "${provider.capabilityName}:collection",
                                kind = provider.capabilityName,
                                producer = EvidenceProducer(
                                    id = provider.capabilityName,
                                    boundary = "provider-declared",
                                ),
                                status = EvidenceStatus.FAILED,
                                limitations = listOf(it::class.java.simpleName),
                            )
                        }
                }
                CapabilityStatus.UNSUPPORTED -> evidence += unavailableEvidence(provider, EvidenceStatus.UNSUPPORTED)
                CapabilityStatus.FAILED -> evidence += unavailableEvidence(provider, EvidenceStatus.FAILED)
                CapabilityStatus.WITHHELD -> evidence += unavailableEvidence(provider, EvidenceStatus.WITHHELD)
            }
        }

        requireUniqueEvidenceIds(evidence)

        val completedAt = clock.nowEpochMillis()
        check(completedAt <= challenge.expiresAtEpochMillis) { "challenge expired during collection" }

        val unsigned = proofEncoder.encode(
            ProofInput(
                challenge = challenge.copy(value = challenge.value.copyOf()),
                proofId = proofIdGenerator.newProofId(),
                startedAtEpochMillis = startedAt,
                completedAtEpochMillis = completedAt,
                capabilities = capabilities,
                evidence = evidence,
            ),
        )
        require(unsigned.bytes.isNotEmpty()) { "proof encoder returned an empty payload" }

        val signed = proofSigner.sign(unsigned)
        require(signed.bytes.isNotEmpty()) { "proof signer returned an empty payload" }
        return proofTransport.submit(signed)
    }

    private fun validateChallenge(challenge: Challenge, requestedProfile: String) {
        require(challenge.value.size in MIN_CHALLENGE_BYTES..MAX_CHALLENGE_BYTES) {
            "challenge length must be between $MIN_CHALLENGE_BYTES and $MAX_CHALLENGE_BYTES bytes"
        }
        require(challenge.protocolVersion == DRAFT_PROTOCOL_VERSION) {
            "unsupported protocol version"
        }
        require(challenge.profile == requestedProfile) { "challenge profile mismatch" }
        require(clock.nowEpochMillis() <= challenge.expiresAtEpochMillis) { "challenge already expired" }
    }

    private fun unavailableEvidence(
        provider: EvidenceProvider,
        status: EvidenceStatus,
    ): EvidenceRecord = EvidenceRecord(
        id = "${provider.capabilityName}:availability",
        kind = provider.capabilityName,
        producer = EvidenceProducer(
            id = provider.capabilityName,
            boundary = "provider-declared",
        ),
        status = status,
    )

    private fun requireUniqueEvidenceIds(records: List<EvidenceRecord>) {
        val ids = HashSet<String>(records.size)
        for (record in records) {
            require(ids.add(record.id)) { "duplicate evidence id: ${record.id}" }
        }
    }

    public companion object {
        public const val DRAFT_PROTOCOL_VERSION: String = "0.1-draft"
        public const val MIN_CHALLENGE_BYTES: Int = 16
        public const val MAX_CHALLENGE_BYTES: Int = 384
    }
}
