package org.nullweave.parc.demo

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var serverInput: EditText
    private lateinit var attestButton: Button
    private lateinit var replayButton: Button
    private lateinit var output: TextView
    private var lastSubmission: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())

        attestButton.setOnClickListener {
            setBusy(true, "Requesting challenge and signing with Android Keystore…")
            Thread {
                runCatching { performAttestation(normalizedServer()) }
                    .onSuccess { run ->
                        lastSubmission = run.submission.toString()
                        runOnUiThread {
                            output.text = run.response.toString(2)
                            setBusy(false)
                        }
                    }
                    .onFailure(::showFailure)
            }.start()
        }

        replayButton.setOnClickListener {
            val submission = lastSubmission
            if (submission == null) {
                output.text = "No signed submission is available to replay."
                return@setOnClickListener
            }
            setBusy(true, "Submitting the identical signed proof again…")
            Thread {
                runCatching {
                    postJson("${normalizedServer()}/v1/verify", JSONObject(submission))
                }.onSuccess { response ->
                    runOnUiThread {
                        output.text = response.toString(2)
                        setBusy(false)
                    }
                }.onFailure(::showFailure)
            }.start()
        }
    }

    private fun buildUi(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 24.dp, 24.dp, 24.dp)
        }
        container.addView(TextView(this).apply {
            text = "PARC Runnable Android Demo"
            textSize = 24f
        })
        container.addView(TextView(this).apply {
            text = "Live challenge → Android Keystore P-256 signature → verifier → replay test"
            textSize = 15f
            setPadding(0, 8.dp, 0, 16.dp)
        })
        serverInput = EditText(this).apply {
            hint = "Verifier URL"
            setText("http://10.0.2.2:8787")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        container.addView(serverInput)
        attestButton = Button(this).apply { text = "Run attestation" }
        replayButton = Button(this).apply {
            text = "Replay last signed proof"
            isEnabled = false
        }
        container.addView(attestButton)
        container.addView(replayButton)
        output = TextView(this).apply {
            text = "Start parc-verifier-server and enter a reachable URL."
            textSize = 13f
            setTextIsSelectable(true)
            setPadding(0, 16.dp, 0, 24.dp)
        }
        container.addView(output)
        return ScrollView(this).apply { addView(container) }
    }

    private fun performAttestation(server: String): AttestationRun {
        require(server.startsWith("http://") || server.startsWith("https://")) {
            "Verifier URL must begin with http:// or https://"
        }
        val challenge = postJson(
            "$server/v1/challenges",
            JSONObject().put("profile", PROFILE),
        )
        require(challenge.getString("protocolVersion") == PROTOCOL_VERSION) {
            "Unsupported verifier protocol ${challenge.getString("protocolVersion")}"
        }
        val challengeBytes = decodeBase64Url(challenge.getString("challenge"))
        require(challengeBytes.size in 16..384) { "Challenge length is outside the draft range" }

        val keyMaterial = getOrCreateKeyMaterial()
        val payload = buildProof(challenge, keyMaterial)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyMaterial.keyPair.private)
            update(payload)
            sign()
        }
        val submission = JSONObject()
            .put("challengeId", challenge.getString("challengeId"))
            .put("payload", encodeBase64Url(payload))
            .put("publicKey", encodeBase64Url(keyMaterial.keyPair.public.encoded))
            .put("signature", encodeBase64Url(signature))
            .put("algorithm", "ES256")
        return AttestationRun(
            response = postJson("$server/v1/verify", submission),
            submission = submission,
        )
    }

    private fun buildProof(challenge: JSONObject, keyMaterial: KeyMaterial): JSONObject {
        val signingDigest = appSigningCertificateDigest()
        val claims = JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("device", Build.DEVICE)
            .put("product", Build.PRODUCT)
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("securityPatch", Build.VERSION.SECURITY_PATCH)
            .put("bootloader", Build.BOOTLOADER)
            .put(
                "buildFingerprintSha256",
                sha256Base64Url(Build.FINGERPRINT.toByteArray(Charsets.UTF_8)),
            )
            .put("applicationSigningCertificateSha256", signingDigest)
        keyMaterial.securityClaims.keys().forEach { key ->
            claims.put(key, keyMaterial.securityClaims.get(key))
        }

        val evidence = JSONObject()
            .put("id", "android-keystore-runtime-summary")
            .put("kind", "parc.android.runtime.summary")
            .put(
                "producer",
                JSONObject()
                    .put("id", "parc-android-demo")
                    .put("boundary", "ordinary-android-application")
                    .put("mediation", "android-framework-and-keystore"),
            )
            .put("status", "present")
            .put("observedAt", Instant.now().toString())
            .put("claims", claims)
            .put("dependencies", JSONArray())
            .put(
                "limitations",
                JSONArray()
                    .put("application-visible-state-can-be-mediated-by-a-privileged-attacker")
                    .put("keystore-security-level-varies-by-device")
                    .put("android-attestation-extension-is-not-yet-appraised-by-the-verifier"),
            )

        return JSONObject()
            .put("protocolVersion", PROTOCOL_VERSION)
            .put("profile", challenge.getString("profile"))
            .put("proofId", UUID.randomUUID().toString())
            .put("challenge", challenge.getString("challenge"))
            .put("issuedAt", Instant.now().toString())
            .put("expiresAt", challenge.getString("expiresAt"))
            .put(
                "subject",
                JSONObject()
                    .put("type", "pairwise")
                    .put("value", subjectFromPublicKey(keyMaterial.keyPair.public.encoded)),
            )
            .put(
                "application",
                JSONObject()
                    .put("packageName", packageName)
                    .put("versionCode", packageVersionCode())
                    .put("signingCertificateSha256", signingDigest)
                    .put("buildId", BuildConfig.VERSION_NAME)
                    .put("bindingStrength", "measured"),
            )
            .put(
                "capabilities",
                JSONArray().put(
                    JSONObject()
                        .put("name", "parc.android.keystore.ec-p256")
                        .put("status", "available"),
                ),
            )
            .put("evidence", JSONArray().put(evidence))
            .put(
                "continuity",
                JSONObject()
                    .put(
                        "events",
                        JSONArray()
                            .put(event("challenge-received", 1))
                            .put(event("evidence-collected", 2))
                            .put(event("proof-signed", 3)),
                    )
                    .put(
                        "edges",
                        JSONArray()
                            .put(edge("challenge-received", "evidence-collected"))
                            .put(edge("evidence-collected", "proof-signed")),
                    ),
            )
            .put(
                "signing",
                JSONObject()
                    .put("format", "parc-demo-es256-v1")
                    .put("algorithm", "ES256")
                    .put("credentialRefs", JSONArray())
                    .put("certificateChain", keyMaterial.certificateChain),
            )
    }

    private fun getOrCreateKeyMaterial(): KeyMaterial {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val keyPair = if (keyStore.containsAlias(KEY_ALIAS)) {
            KeyPair(
                keyStore.getCertificate(KEY_ALIAS).publicKey,
                keyStore.getKey(KEY_ALIAS, null) as PrivateKey,
            )
        } else {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").run {
                initialize(
                    KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setUserAuthenticationRequired(false)
                        .build(),
                )
                generateKeyPair()
            }
        }

        val chain = JSONArray()
        keyStore.getCertificateChain(KEY_ALIAS)?.forEach { certificate ->
            chain.put(encodeBase64Url(certificate.encoded))
        }
        return KeyMaterial(keyPair, keySecurityClaims(keyPair.private), chain)
    }

    private fun keySecurityClaims(privateKey: PrivateKey): JSONObject {
        val claims = JSONObject()
            .put("keyAlgorithm", privateKey.algorithm)
            .put("keyProvider", privateKey.format ?: "AndroidKeyStore")
        runCatching {
            val factory = KeyFactory.getInstance(privateKey.algorithm, "AndroidKeyStore")
            val info = factory.getKeySpec(privateKey, KeyInfo::class.java)
            claims.put("insideSecureHardware", info.isInsideSecureHardware)
            if (Build.VERSION.SDK_INT >= 28) {
                claims.put("strongBoxBacked", info.isStrongBoxBacked)
            }
            if (Build.VERSION.SDK_INT >= 31) {
                claims.put("keystoreSecurityLevel", securityLevelName(info.securityLevel))
            } else {
                claims.put(
                    "keystoreSecurityLevel",
                    if (info.isInsideSecureHardware) "hardware-backed-unspecified" else "software",
                )
            }
        }.onFailure { error -> claims.put("keyInfoError", error.javaClass.simpleName) }
        return claims
    }

    private fun securityLevelName(level: Int): String = when (level) {
        KeyProperties.SECURITY_LEVEL_SOFTWARE -> "software"
        KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "trusted-environment"
        KeyProperties.SECURITY_LEVEL_STRONGBOX -> "strongbox"
        KeyProperties.SECURITY_LEVEL_UNKNOWN_SECURE -> "unknown-secure"
        else -> "unknown"
    }

    @Suppress("DEPRECATION")
    private fun appSigningCertificateDigest(): String {
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val info = packageManager.getPackageInfo(packageName, flags)
        val bytes = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            info.signatures?.firstOrNull()?.toByteArray()
        }
        return bytes?.let(::sha256Base64Url) ?: "unavailable"
    }

    @Suppress("DEPRECATION")
    private fun packageVersionCode(): Long {
        val info = packageManager.getPackageInfo(packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }

    private fun postJson(endpoint: String, body: JSONObject): JSONObject {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("content-type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code: $text")
            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun event(id: String, sequence: Int): JSONObject = JSONObject()
        .put("id", id)
        .put("kind", id)
        .put("assertedBy", "parc-android-demo")
        .put("sequence", sequence)

    private fun edge(before: String, after: String): JSONObject = JSONObject()
        .put("before", before)
        .put("after", after)

    private fun subjectFromPublicKey(encoded: ByteArray): String =
        encodeBase64Url(MessageDigest.getInstance("SHA-256").digest(encoded).copyOfRange(0, 16))

    private fun sha256Base64Url(value: ByteArray): String =
        encodeBase64Url(MessageDigest.getInstance("SHA-256").digest(value))

    private fun encodeBase64Url(value: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value)

    private fun decodeBase64Url(value: String): ByteArray = Base64.getUrlDecoder().decode(value)

    private fun normalizedServer(): String = serverInput.text.toString().trim().trimEnd('/')

    private fun showFailure(error: Throwable) {
        runOnUiThread {
            output.text = "${error.javaClass.simpleName}: ${error.message ?: "unknown error"}"
            setBusy(false)
        }
    }

    private fun setBusy(busy: Boolean, message: String? = null) {
        attestButton.isEnabled = !busy
        replayButton.isEnabled = !busy && lastSubmission != null
        if (message != null) output.text = message
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private data class KeyMaterial(
        val keyPair: KeyPair,
        val securityClaims: JSONObject,
        val certificateChain: JSONArray,
    )

    private data class AttestationRun(
        val response: JSONObject,
        val submission: JSONObject,
    )

    private companion object {
        const val PROTOCOL_VERSION = "0.1-draft"
        const val PROFILE = "parc.basic.application"
        const val KEY_ALIAS = "parc-demo-es256-v1"
    }
}
