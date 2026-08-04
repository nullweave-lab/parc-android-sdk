# Runnable Control-Plane Integration

The SDK branch now contains an installable `sample` APK and a concrete adapter for the runnable control plane in `nullweave-lab/parc-infra` PR #2.

## Implemented flow

The sample application can:

1. exchange application client credentials for a bearer token;
2. register the Android installation as a test device;
3. encrypt the returned test device secret with an AES-GCM key held by Android Keystore;
4. collect local runtime signals;
5. request a server challenge;
6. canonicalise evidence and calculate the challenge-bound HMAC proof expected by the test control plane;
7. submit the proof and display the server decision.

## Collected signals

The initial collector reports:

- `ro.boot.verifiedbootstate`;
- SELinux enforcement file state;
- known `su`, Magisk, KernelSU, and APatch paths;
- debug build/debugger state;
- known Xposed/Substrate classes;
- Frida, Xposed, LSPosed, Substrate, and Zygisk names in `/proc/self/maps`;
- SDK level, build type, and build tags.

These signals are real Android observations but are not yet authoritative. A rooted or hooked environment may forge Java, property, filesystem, process-map, and network views. The runnable integration exists to exercise the complete client/server lifecycle before KeyMint, StrongBox, key attestation, native probes, and cross-check scheduling are added.

## Build

```text
gradle :sdk:testDebugUnitTest :sample:assembleDebug
```

The APK is produced at:

```text
sample/build/outputs/apk/debug/sample-debug.apk
```

For an Android emulator, the sample defaults to `http://10.0.2.2:8080`, which reaches a control plane running on the development host. Cleartext HTTP is enabled only in this test sample; production integration must use TLS and network-security configuration.
