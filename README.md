# parc-android-sdk

Android SDK contracts with two runnable Android applications.

## `app`: direct Keystore/verifier demo

The `app` module requests a live challenge, creates or loads a P-256 key in `AndroidKeyStore`, gathers an explicit Android/key summary, signs the exact proof payload with `SHA256withECDSA`, submits it to `parc-verifier-server`, displays the result, and can replay the identical request.

```sh
gradle :app:assembleDebug
```

See [ANDROID-DEMO.md](ANDROID-DEMO.md).

## `sample`: control-plane integration sample

The existing `sample` module demonstrates registration, client credentials, device-secret storage, evidence collection, and a control-plane attestation decision flow.

```sh
gradle :sample:assembleDebug
```

See [RUNNABLE-CONTROL-PLANE.md](RUNNABLE-CONTROL-PLANE.md).

## SDK module

The `sdk` module contains the challenge, evidence, proof, signing, transport, verifier-result, and control-plane contracts used by the applications.

## Platform baseline

- `minSdk 26`
- `compileSdk 36`
- JDK 17
- AGP 8.13.2 / Gradle 8.13
- Kotlin 2.3.0

## Current boundary

Both APKs are buildable applications. The direct demo performs real Android Keystore signing and real HTTP submission. The reference verifier currently checks ES256 possession and protocol mechanics, but full Android key-attestation extension validation, root trust, revocation, and production TLS remain incomplete.

## License

Apache License 2.0. See `LICENSE`.
