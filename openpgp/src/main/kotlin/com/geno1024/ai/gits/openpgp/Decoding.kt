package com.geno1024.ai.gits.openpgp

import org.bouncycastle.openpgp.PGPUtil
import java.io.InputStream

/**
 * Wraps this byte source as a BouncyCastle decoder stream, transparently handling both
 * ASCII-armored and binary OpenPGP encodings.
 *
 * A byte order mark is dropped first: it is invisible in every editor there is, it
 * survives a save, and BouncyCastle treats the key it sits in front of as not a key at all.
 */
internal fun ByteArray.decoderStream(): InputStream =
    PGPUtil.getDecoderStream(withoutByteOrderMark().inputStream())

private fun ByteArray.withoutByteOrderMark(): ByteArray =
    if (size >= 3 && this[0] == 0xEF.toByte() && this[1] == 0xBB.toByte() && this[2] == 0xBF.toByte()) {
        copyOfRange(3, size)
    } else {
        this
    }

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
