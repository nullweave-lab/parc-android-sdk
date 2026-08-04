# Android JNI Runtime Probes

The SDK now packages `libparc_probe_jni.so` for `arm64-v8a`, `armeabi-v7a`, and `x86_64`.

The JNI collector performs bounded, current-process measurements of:

- `/proc/self/maps` record, executable, anonymous-executable, deleted-backing, suspicious-name, and malformed counts;
- `/proc/self/mountinfo` filesystem-type and malformed counts;
- `/proc/self/status` `TracerPid`, `NoNewPrivs`, and `Seccomp` values;
- whether parser line limits were reached;
- a non-cryptographic FNV-1a challenge correlation tag.

It does not return raw mapped paths, mount paths, or unrelated process data. The native observation is included inside the challenge-authenticated control-plane evidence object.

## Boundary

These measurements describe only the application process's current procfs and mount-namespace view. Higher-privilege software can mediate, race, or forge that view. Anonymous executable mappings, overlay mounts, suspicious names, or a nonzero tracer PID are evidence values, not automatic compromise verdicts.

The FNV challenge tag only demonstrates that the native call received challenge bytes; the outer HMAC or ES256 proof provides the cryptographic challenge binding.

## Build verification

CI installs NDK `27.2.12479018`, builds both APKs, and fails unless each APK contains:

- `lib/arm64-v8a/libparc_probe_jni.so`
- `lib/armeabi-v7a/libparc_probe_jni.so`
- `lib/x86_64/libparc_probe_jni.so`
