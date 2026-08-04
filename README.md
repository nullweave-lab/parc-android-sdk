# parc-android-sdk

Android SDK contracts with two runnable Android applications.

## Direct Keystore/verifier demo (`app`)

Requests a live challenge, creates or loads a P-256 key in `AndroidKeyStore`, gathers an explicit Android/key summary, signs the exact proof payload with `SHA256withECDSA`, submits it to `parc-verifier-server`, displays the result, and can replay the identical request.

```sh
gradle :app:assembleDebug
```

See [ANDROID-DEMO.md](ANDROID-DEMO.md).

## Control-plane sample (`sample`)

Demonstrates registration, client credentials, protected device-secret storage, evidence collection, and a control-plane attestation decision flow.

```sh
gradle :sample:assembleDebug
```

See [RUNNABLE-CONTROL-PLANE.md](RUNNABLE-CONTROL-PLANE.md).

## SDK module

The `sdk` module contains the challenge, evidence, proof, signing, transport, verifier-result, and control-plane contracts used by both applications.

## Platform baseline

- `minSdk 26`
- `compileSdk 36`
- JDK 17
- AGP 8.13.2 / Gradle 8.13
- Kotlin 2.3.0

## Current boundary

Both APKs are built by CI. The direct demo performs Android Keystore signing and real HTTP submission. The reference verifier checks ES256 possession and protocol mechanics; full Android key-attestation extension validation, root trust, revocation, Verified Boot appraisal, and production TLS remain incomplete.

## License

Apache License 2.0. See `LICENSE`.
