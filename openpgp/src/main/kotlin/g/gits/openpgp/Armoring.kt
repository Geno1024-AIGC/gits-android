package g.gits.openpgp

import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRing
import java.io.ByteArrayOutputStream

private fun armored(encode: ArmoredOutputStream.() -> Unit): ByteArray =
    ByteArrayOutputStream().also { out ->
        ArmoredOutputStream(out).use { it.encode() }
    }.toByteArray()

/** ASCII-armors a secret keyring, the layout GnuPG expects from `gpg --export`. */
fun PGPSecretKeyRing.armored(): ByteArray = armored { encode(this) }

/** ASCII-armors a public keyring, the layout GnuPG expects from `gpg --armor --export`. */
fun PGPPublicKeyRing.armored(): ByteArray = armored { encode(this) }
