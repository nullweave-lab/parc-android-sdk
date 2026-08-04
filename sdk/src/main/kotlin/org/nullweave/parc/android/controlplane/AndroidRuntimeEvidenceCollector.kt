package org.nullweave.parc.android.controlplane

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Debug
import java.io.File
import java.util.concurrent.TimeUnit

public class AndroidRuntimeEvidenceCollector(
    context: Context,
) {
    private val appContext: Context = context.applicationContext

    public fun collect(challenge: ByteArray = ByteArray(0)): Map<String, Any?> = linkedMapOf(
        "boot_state" to (readProperty("ro.boot.verifiedbootstate") ?: "unknown"),
        "selinux_enforcing" to readSelinuxEnforcing(),
        "root_detected" to detectRoot(),
        "hook_detected" to detectHooking(),
        "debuggable" to isDebuggable(),
        "sdk_int" to Build.VERSION.SDK_INT,
        "build_type" to Build.TYPE,
        "build_tags" to (Build.TAGS ?: ""),
        "native_runtime" to NativeRuntimeProbe.collect(challenge),
    )

    private fun isDebuggable(): Boolean =
        (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 || Debug.isDebuggerConnected()

    private fun readSelinuxEnforcing(): Boolean = runCatching {
        File("/sys/fs/selinux/enforce").readText().trim() == "1"
    }.getOrDefault(false)

    private fun detectRoot(): Boolean {
        if ((Build.TAGS ?: "").contains("test-keys", ignoreCase = true)) return true
        val knownPaths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/data/adb/magisk",
            "/data/adb/ksu",
            "/data/adb/ap",
        )
        return knownPaths.any { File(it).exists() }
    }

    private fun detectHooking(): Boolean {
        val classes = arrayOf(
            "de.robv.android.xposed.XposedBridge",
            "de.robv.android.xposed.XC_MethodHook",
            "com.saurik.substrate.MS\$2",
        )
        if (classes.any { runCatching { Class.forName(it) }.isSuccess }) return true
        return runCatching {
            val maps = File("/proc/self/maps").readText().lowercase()
            listOf("frida", "xposed", "lsposed", "substrate", "zygisk").any(maps::contains)
        }.getOrDefault(false)
    }

    private fun readProperty(name: String): String? = runCatching {
        val process = ProcessBuilder("getprop", name).redirectErrorStream(true).start()
        if (!process.waitFor(300, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            return@runCatching null
        }
        process.inputStream.bufferedReader().use { it.readText().trim().ifEmpty { null } }
    }.getOrNull()
}
