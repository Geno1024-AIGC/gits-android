package g.gits.android.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts a secret so that what lands on disk is not the secret itself. */
interface SecretBox {
    fun seal(plaintext: ByteArray): ByteArray
    fun open(sealed: ByteArray): ByteArray
}

/**
 * Encrypts with a key that only the Android keystore can use.
 *
 * A token stored beside the data it protects, under a key derived from that same
 * device, is protection against nothing but casual inspection. Holding the key in the
 * keystore means the stored bytes are useless on any other device, and off this one
 * without the user's lock screen.
 *
 * The nonce is written in front of the ciphertext, as GCM requires.
 */
class AndroidKeystoreBox(private val alias: String) : SecretBox {

    override fun seal(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val nonce = cipher.iv
        return nonce + cipher.doFinal(plaintext)
    }

    override fun open(sealed: ByteArray): ByteArray {
        require(sealed.size > NONCE_BYTES) { "stored secret is too short to be anything" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_BYTES),
        )
        return cipher.doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES)
    }

    private fun key(): SecretKey {
        val existing = keyStore().getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val BITS = 256
    }
}
