# Runnable Android Keystore Demo

The `app` module is an installable Android client for the reference verifier. It coexists with the `sample` control-plane application.

## Executed flow

1. Request a live challenge from `POST /v1/challenges`.
2. Generate or load a P-256 key in `AndroidKeyStore`.
3. Collect a bounded Android, application-signing, and KeyInfo summary.
4. Construct the draft proof JSON.
5. Sign the exact UTF-8 payload with `SHA256withECDSA`.
6. Submit the X.509/SPKI public key, DER ECDSA signature, and payload to `POST /v1/verify` using algorithm `ES256`.
7. Display the verifier result.
8. Replay the identical signed request to demonstrate replay rejection.

## Build

```sh
gradle :app:assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

CI uploads it as `parc-android-keystore-demo-debug-apk`.

## Connect

Start `parc-verifier-server` on a reachable computer.

- Emulator: `http://10.0.2.2:8787`
- Physical device over LAN: `http://<computer-lan-ip>:8787`
- USB: run `adb reverse tcp:8787 tcp:8787`, then use `http://127.0.0.1:8787`

Cleartext HTTP is enabled only so local testing works. Production deployment requires authenticated HTTPS.

## Boundary

The APK performs real Android Keystore signing and reports Android's KeyInfo security classification. The verifier validates ES256 possession and protocol mechanics. It does not yet validate the Android key-attestation extension, certificate roots, revocation, or Verified Boot claims.
