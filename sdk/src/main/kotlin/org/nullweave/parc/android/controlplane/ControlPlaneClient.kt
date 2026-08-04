package org.nullweave.parc.android.controlplane

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

public class ControlPlaneClient(
    baseUrl: String,
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 10_000,
) {
    private val endpoint: String = baseUrl.trimEnd('/')

    public fun exchangeToken(credentials: ClientCredentials): String {
        val response = request(
            path = "/v1/auth/token",
            body = JSONObject()
                .put("client_id", credentials.clientId)
                .put("client_secret", credentials.clientSecret),
        )
        return response.getString("access_token")
    }

    public fun registerDevice(token: String, externalId: String): RegisteredDevice {
        val response = request(
            path = "/v1/devices",
            token = token,
            expectedStatus = 201,
            body = JSONObject().put("external_id", externalId),
        )
        return RegisteredDevice(
            deviceId = response.getString("device_id"),
            externalId = response.getString("external_id"),
            deviceSecret = response.getString("device_secret"),
        )
    }

    public fun issueChallenge(token: String, deviceId: String): IssuedChallenge {
        val response = request(
            path = "/v1/challenges",
            token = token,
            expectedStatus = 201,
            body = JSONObject().put("device_id", deviceId),
        )
        return IssuedChallenge(
            challengeId = response.getString("challenge_id"),
            nonce = response.getString("nonce"),
            deviceId = response.getString("device_id"),
            expiresAt = response.getString("expires_at"),
        )
    }

    public fun submitProof(
        token: String,
        device: RegisteredDevice,
        challenge: IssuedChallenge,
        evidence: Map<String, Any?>,
        submissionId: String = UUID.randomUUID().toString(),
    ): ControlPlaneDecision {
        val mac = ProofCodec.calculateMac(
            deviceSecret = device.deviceSecret,
            challengeId = challenge.challengeId,
            nonce = challenge.nonce,
            deviceId = device.deviceId,
            evidence = evidence,
        )
        val response = request(
            path = "/v1/proofs",
            token = token,
            body = JSONObject()
                .put("challenge_id", challenge.challengeId)
                .put("submission_id", submissionId)
                .put("device_id", device.deviceId)
                .put("evidence", JSONObject(evidence))
                .put("proof_mac", mac),
        )
        return ControlPlaneDecision(
            decisionId = response.getString("decision_id"),
            challengeId = response.getString("challenge_id"),
            decision = response.getString("decision"),
            reasons = response.getJSONArray("reasons").toStringList(),
            idempotentReplay = response.getBoolean("idempotent_replay"),
        )
    }

    public fun attest(
        credentials: ClientCredentials,
        device: RegisteredDevice,
        collector: AndroidRuntimeEvidenceCollector,
    ): ControlPlaneDecision {
        val token = exchangeToken(credentials)
        val challenge = issueChallenge(token, device.deviceId)
        return submitProof(token, device, challenge, collector.collect())
    }

    private fun request(
        path: String,
        body: JSONObject,
        token: String? = null,
        expectedStatus: Int = 200,
    ): JSONObject {
        val connection = URL(endpoint + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (status != expectedStatus) {
                throw ControlPlaneException(status, text.ifBlank { connection.responseMessage })
            }
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONArray.toStringList(): List<String> = buildList(length()) {
        for (index in 0 until length()) add(getString(index))
    }
}
