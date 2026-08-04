package org.nullweave.parc.sample

import android.app.Activity
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.nullweave.parc.android.controlplane.AndroidRuntimeEvidenceCollector
import org.nullweave.parc.android.controlplane.ClientCredentials
import org.nullweave.parc.android.controlplane.ControlPlaneClient
import org.nullweave.parc.android.controlplane.DeviceSecretStore
import org.nullweave.parc.android.controlplane.NativeRuntimeProbe
import org.nullweave.parc.android.controlplane.RegisteredDevice

class MainActivity : Activity() {
    private lateinit var baseUrl: EditText
    private lateinit var clientId: EditText
    private lateinit var clientSecret: EditText
    private lateinit var output: TextView
    private val preferences by lazy { getSharedPreferences("parc-sample", MODE_PRIVATE) }
    private val secretStore by lazy { DeviceSecretStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        baseUrl = field("Base URL", preferences.getString("base_url", "http://10.0.2.2:8080") ?: "")
        clientId = field("Client ID", preferences.getString("client_id", "") ?: "")
        clientSecret = field("Client secret", "").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        output = TextView(this).apply { text = "Register a device, then run attestation." }
        layout.addView(baseUrl)
        layout.addView(clientId)
        layout.addView(clientSecret)
        layout.addView(Button(this).apply {
            text = "Register device"
            setOnClickListener { registerDevice() }
        })
        layout.addView(Button(this).apply {
            text = "Run attestation"
            setOnClickListener { attest() }
        })
        layout.addView(
            output,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        setContentView(layout)

        if (intent.getBooleanExtra(EXTRA_CI_NATIVE_SMOKE, false)) {
            runNativeSmoke()
        }
    }

    private fun runNativeSmoke() {
        val challenge = "activity-smoke-challenge".toByteArray(Charsets.UTF_8)
        val result = NativeRuntimeProbe.collect(challenge)
        val versionValid = result["implementation_version"] == "0.1.0"
        val lengthValid = (result["challenge_length"] as? Number)?.toInt() == challenge.size
        val tagValid = (result["challenge_tag_fnv1a64"] as? String)?.length == 16
        val success = versionValid && lengthValid && tagValid && !result.containsKey("status")

        if (success) {
            val message = "PARC_JNI_SMOKE_OK length=${challenge.size} version=${result["implementation_version"]}"
            Log.i(SMOKE_TAG, message)
            output.text = "$message\n$result"
        } else {
            val message = "PARC_JNI_SMOKE_FAILED result=$result"
            Log.e(SMOKE_TAG, message)
            output.text = message
        }
    }

    private fun field(hint: String, value: String): EditText = EditText(this).apply {
        this.hint = hint
        setText(value)
    }

    private fun credentials(): ClientCredentials =
        ClientCredentials(clientId.text.toString().trim(), clientSecret.text.toString())

    private fun registerDevice() = runNetwork {
        val api = ControlPlaneClient(baseUrl.text.toString())
        val token = api.exchangeToken(credentials())
        val externalId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            ?: "android-device"
        val device = api.registerDevice(token, externalId)
        secretStore.put(device.deviceId, device.deviceSecret)
        preferences.edit()
            .putString("device_id", device.deviceId)
            .putString("external_id", device.externalId)
            .putString("base_url", baseUrl.text.toString())
            .putString("client_id", clientId.text.toString())
            .apply()
        "Registered device ${device.deviceId}"
    }

    private fun attest() = runNetwork {
        val deviceId = preferences.getString("device_id", null) ?: error("register the device first")
        val deviceSecret = secretStore.get(deviceId) ?: error("stored device secret unavailable")
        val externalId = preferences.getString("external_id", "android-device") ?: "android-device"
        val api = ControlPlaneClient(baseUrl.text.toString())
        val decision = api.attest(
            credentials = credentials(),
            device = RegisteredDevice(deviceId, externalId, deviceSecret),
            collector = AndroidRuntimeEvidenceCollector(this),
        )
        "Decision: ${decision.decision}\nReasons: ${decision.reasons.joinToString()}\nID: ${decision.decisionId}"
    }

    private fun runNetwork(block: () -> String) {
        output.text = "Running…"
        Thread {
            val result = runCatching(block).fold(
                onSuccess = { it },
                onFailure = { "Error: ${it.message}" },
            )
            runOnUiThread { output.text = result }
        }.start()
    }

    private companion object {
        const val EXTRA_CI_NATIVE_SMOKE: String = "parc_ci_native_smoke"
        const val SMOKE_TAG: String = "PArCNativeProbeSmoke"
    }
}
