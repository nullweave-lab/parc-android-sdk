package org.nullweave.parc.android.controlplane

public data class ClientCredentials(
    public val clientId: String,
    public val clientSecret: String,
)

public data class RegisteredDevice(
    public val deviceId: String,
    public val externalId: String,
    public val deviceSecret: String,
)

public data class IssuedChallenge(
    public val challengeId: String,
    public val nonce: String,
    public val deviceId: String,
    public val expiresAt: String,
)

public data class ControlPlaneDecision(
    public val decisionId: String,
    public val challengeId: String,
    public val decision: String,
    public val reasons: List<String>,
    public val idempotentReplay: Boolean,
)

public class ControlPlaneException(
    public val statusCode: Int,
    message: String,
) : RuntimeException(message)
