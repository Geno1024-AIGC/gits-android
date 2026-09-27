package g.gits.openpgp

import org.bouncycastle.openpgp.PGPUtil
import java.io.InputStream

/**
 * Wraps this byte source as a BouncyCastle decoder stream, transparently handling both
 * ASCII-armored and binary OpenPGP encodings.
 */
internal fun ByteArray.decoderStream(): InputStream =
    PGPUtil.getDecoderStream(this.inputStream())

/** Reads a BouncyCastle `Iterable` into a Kotlin list. */
internal fun <T> Iterable<T>.asList(): List<T> {
    val result = ArrayList<T>()
    for (item in this) result.add(item)
    return result
}

/** Drains an iterator into a Kotlin list. */
internal fun <T> Iterator<T>.asList(): List<T> {
    val result = ArrayList<T>()
    while (hasNext()) result.add(next())
    return result
}
