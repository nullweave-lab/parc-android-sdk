package org.nullweave.parc.android

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ParcClientTest {
    @Test
    fun unsupportedCapabilityRemainsVisible() {
        var encoded: ProofInput? = null
        val client = ParcClient(
            challengeProvider = ChallengeProvider {
                Challenge(
                    value = ByteArray(16) { 1 },
                    protocolVersion = ParcClient.DRAFT_PROTOCOL_VERSION,
                    profile = it,
                    expiresAtEpochMillis = 2_000,
                )
            },
            evidenceProviders = listOf(
                object : EvidenceProvider {
                    override val capabilityName: String = "parc.test.unsupported"
                    override suspend fun capability(): Capability =
                        Capability(capabilityName, CapabilityStatus.UNSUPPORTED)
                    override suspend fun collect(context: CollectionContext): EvidenceRecord =
                        error("collect must not run")
                },
            ),
            proofEncoder = ProofEncoder {
                encoded = it
                UnsignedProof("unsigned".encodeToByteArray(), "application/test")
            },
            proofSigner = ProofSigner {
                SignedProof("signed".encodeToByteArray(), "application/test")
            },
            proofTransport = ProofTransport {
                VerificationResult(VerificationStatus.INDETERMINATE, listOf("test"))
            },
            clock = Clock { 1_000 },
            proofIdGenerator = ProofIdGenerator { "proof-1" },
        )

        val result = runSuspend { client.attest("parc.test") }
        assertEquals(VerificationStatus.INDETERMINATE, result.status)
        assertEquals(EvidenceStatus.UNSUPPORTED, encoded!!.evidence.single().status)
    }

    @Test
    fun shortChallengeIsRejected() {
        val client = ParcClient(
            challengeProvider = ChallengeProvider {
                Challenge(ByteArray(4), ParcClient.DRAFT_PROTOCOL_VERSION, it, 2_000)
            },
            evidenceProviders = emptyList(),
            proofEncoder = ProofEncoder { error("must not encode") },
            proofSigner = ProofSigner { error("must not sign") },
            proofTransport = ProofTransport { error("must not submit") },
            clock = Clock { 1_000 },
            proofIdGenerator = ProofIdGenerator { "proof-1" },
        )

        assertFailsWith<IllegalArgumentException> {
            runSuspend { client.attest("parc.test") }
        }
    }
}

private fun <T> runSuspend(block: suspend () -> T): T {
    var completed: Result<T>? = null
    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                completed = result
            }
        },
    )
    return checkNotNull(completed) { "test coroutine suspended unexpectedly" }.getOrThrow()
}
