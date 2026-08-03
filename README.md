# parc-android-sdk

Experimental Android SDK contract for requesting challenges, collecting public evidence, constructing a PARC payload, signing it through an injected adapter, submitting it to a verifier, and returning the verifier's structured result.

> **Status:** Alpha. No production cryptographic container or network protocol is frozen.

## Current platform baseline

- Android library module
- `minSdk 26`
- `compileSdk 36`
- JDK 17
- AGP 8.13.2 / Gradle 8.13
- Kotlin 2.3.0

The initial module is pure Kotlin and therefore ABI-neutral. Native evidence providers are optional adapters supplied by `parc-native-probes`; no native binary is bundled in this PR.

## Public API boundaries

The SDK defines interfaces for:

- challenge acquisition;
- capability and evidence providers;
- proof encoding;
- proof signing;
- proof transport;
- structured verifier responses;
- cancellation-aware orchestration through `suspend` functions.

It does not implement private rules, hidden probes, business authorization, production endpoints, certificate trust stores, or unreviewed anti-analysis behavior.

## Build

```sh
gradle :sdk:testDebugUnitTest
```

## License

Apache License 2.0. See `LICENSE`.
