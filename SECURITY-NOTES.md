# SDK Security Notes

- Do not embed verifier secrets, private keys, API tokens, or production endpoints in the SDK.
- Do not treat a local provider result as independently trustworthy without appraising its producer and dependencies.
- Do not automatically retry with a weaker profile after failure.
- Do not log challenge bytes, proof bytes, certificate chains, stable identifiers, or detailed anomaly payloads by default.
- Do not run business authorization inside the SDK.
- Do not convert provider exceptions into successful empty evidence.
- Do not load an arbitrary native library path supplied by untrusted input.
- Do not claim that obfuscation, anti-debugging, or process termination creates attestation assurance.
