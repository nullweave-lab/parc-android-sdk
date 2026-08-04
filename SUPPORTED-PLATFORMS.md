# Supported Platforms and Capability Policy

## Android baseline

The initial SDK module targets Android API 26 and later. This baseline is an engineering choice for the alpha SDK, not a statement that every PARC capability exists on API 26.

## Capability-specific support

Hardware-backed key generation, key attestation, StrongBox, KeyMint versions, certificate-chain properties, Verified Boot claims, and vendor behavior vary by device and release. Each provider must report its own capability status and must not infer support solely from OS version.

## ABI policy

This PR contains no native binaries and is ABI-neutral. When `parc-native-probes` artifacts are integrated, each release must publish the exact ABI matrix and test status. Android NDK-supported ABI names must be used without aliases.

## Privilege profiles

The public API must distinguish ordinary application capability from privileged application, system, OEM, kernel, or secure-world integration. A stronger profile must never be silently emulated by a weaker one.
