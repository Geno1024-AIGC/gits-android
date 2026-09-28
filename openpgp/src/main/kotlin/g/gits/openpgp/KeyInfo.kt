package g.gits.openpgp

import java.util.Date

/**
 * A key parsed out of a keyring, described well enough to choose between them in a UI.
 *
 * [fingerprint] identifies the key that actually performs the operation. That is
 * normally a signing subkey rather than the primary key, so [masterFingerprint] is
 * carried alongside it to keep the owning key and its user ids discoverable.
 */
data class KeyInfo(
    val fingerprint: ByteArray,
    val masterFingerprint: ByteArray,
    val keyId: Long,
    val userIds: List<String>,
    val algorithm: Int,
    val isSigningKey: Boolean,
    val isEncryptionKey: Boolean,
    val creationTime: Date,
    /**
     * Whether the private material is under a passphrase.
     *
     * An import may bring a key whose secret is not protected, and that is worth
     * knowing before it is copied anywhere else.
     */
    val isPassphraseProtected: Boolean = true,
) {
    val fingerprintHex: String get() = fingerprint.toHex().uppercase()
    val masterFingerprintHex: String get() = masterFingerprint.toHex().uppercase()
    val keyIdHex: String get() = keyId.toKeyIdHex().uppercase()

    val isSubKey: Boolean get() = !fingerprint.contentEquals(masterFingerprint)

    /** Trailing 64 bits in the grouped form used across Git and GnuPG UIs. */
    val fingerprintAbbreviated: String
        get() = fingerprintHex.takeLast(16).chunked(4).joinToString(" ")

    val primaryUserId: String? get() = userIds.firstOrNull()

    override fun equals(other: Any?): Boolean = this === other ||
        (other is KeyInfo && fingerprint.contentEquals(other.fingerprint))

    override fun hashCode(): Int = fingerprint.contentHashCode()
}
