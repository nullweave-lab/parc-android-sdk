package org.nullweave.parc.demo

import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties

/**
 * KeyInfo has no isStrongBoxBacked property. StrongBox is reported through
 * securityLevel on Android 12 and newer; older releases can only expose the
 * broader insideSecureHardware signal.
 */
internal val KeyInfo.isStrongBoxBacked: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
