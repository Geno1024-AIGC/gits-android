package com.geno1024.ai.gits.data

import android.content.Context
import android.os.Environment
import android.util.Log
import org.apache.sshd.common.config.keys.FilePasswordProvider
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.config.keys.loader.KeyPairResourceParser
import org.apache.sshd.common.config.keys.loader.openssh.OpenSSHKeyPairResourceParser
import org.apache.sshd.common.config.keys.loader.pem.DSSPEMResourceKeyPairParser
import org.apache.sshd.common.config.keys.loader.pem.ECDSAPEMResourceKeyPairParser
import org.apache.sshd.common.config.keys.loader.pem.PKCS8PEMResourceKeyPairParser
import org.apache.sshd.common.config.keys.loader.pem.RSAPEMResourceKeyPairParser
import org.apache.sshd.common.digest.BuiltinDigests
import org.apache.sshd.common.util.io.resource.PathResource
import org.apache.sshd.common.util.security.SecurityUtils
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.transport.sshd.SshdSessionFactory
import java.io.File
import java.util.Base64

/**
 * Where the files of an SSH client are kept, in place of a home directory.
 *
 * Android has no `~` to speak of: it lands somewhere the app may not write, and a
 * known_hosts file has to be created somewhere it can. So this app's storage stands
 * in for the whole of the home directory — config, known_hosts and private keys all
 * live under [directory], which is where the transport is pointed.
 */
object Ssh {

    /** The directory standing in for `~/.ssh`, inside this app's own storage. */
    fun directory(context: Context): File = File(context.filesDir, ".ssh")

    /**
     * Points JGit's SSH transport at this app's storage, before anything connects.
     *
     * The session factory is JVM-wide, so this runs once at start-up. What is left at
     * its defaults is deliberate: an unknown host key is asked about rather than taken
     * or refused, and the keys already in [directory] are the ones tried.
     *
     * One default is overridden here. Android ships a BouncyCastle that claims RSA in
     * its table and then refuses to provide it — a change from Android P — and sshd
     * prefers any registrar it finds over the platform's own providers, so every key
     * read would fail. Declining to register that provider sends the algorithms back
     * to the platform, which provides them. This has to be said before sshd registers
     * anything, and start-up is early enough; said later, it would be too late.
     */
    fun install(context: Context) {
        SecurityUtils.setAPrioriDisabledProvider(SecurityUtils.BOUNCY_CASTLE, true)
        directory(context).mkdirs()
        SshSessionFactory.setInstance(
            SshdSessionFactory().apply {
                setHomeDirectory(context.filesDir)
                setSshDirectory(directory(context))
            },
        )
    }

    /**
     * The names the transport tries on its own, without a config file to point at.
     *
     * These are the ones OpenSSH uses and the ones the transport looks for in [directory],
     * so a key stored under one of them is found — and a key stored under any other name
     * is held but never offered.
     */
    val IDENTITY_NAMES = listOf("id_rsa", "id_dsa", "id_ecdsa", "id_ed25519")

    /** Which of the identities are held, in the order they are named. */
    fun identities(context: Context): List<String> =
        IDENTITY_NAMES.filter { File(directory(context), it).isFile }

    /** The fingerprint of the identity held under [name], or null when it has none to show. */
    fun fingerprint(context: Context, name: String): String? = fingerprintOf(File(directory(context), name))

    /**
     * The fingerprint OpenSSH would print for the key in [file], or null when there is none.
     *
     * This is the same digest the transport and `ssh-keygen` speak — `SHA256:` over the
     * public key as it travels on the wire — so it says what a person who knows this key
     * would recognise. An encrypted key keeps its public half behind a passphrase, and
     * asking for one at the sight of a list is the wrong moment, so such a key is listed
     * under its name alone rather than refused.
     *
     * The parser is picked from the file's own begin marker instead of asking sshd for
     * its registered set: registering the whole set initializes every parser there is,
     * and on a phone that has been seen to fail where the one parser this file needs
     * does not. A failure still says itself, in the log, rather than passing as silence.
     */
    fun fingerprintOf(file: File): String? {
        if (!file.isFile) return null
        return runCatching {
            val lines = file.readLines()
            val parser = parserFor(lines)
                ?: error("No parser for the begin marker of ${file.name}")
            parser
                .loadKeyPairs(null, PathResource(file.toPath()), FilePasswordProvider.EMPTY, lines)
                ?.firstOrNull()
                ?.let { KeyUtils.getFingerPrint(BuiltinDigests.sha256, it.public) }
        }.onFailure { failure ->
            Log.w("Ssh", "No fingerprint for ${file.name}: $failure")
        }.getOrNull()
    }

    /** The parser that reads files beginning with [lines]' begin marker, when there is one. */
    private fun parserFor(lines: List<String>): KeyPairResourceParser? {
        val marker = lines.firstOrNull { it.startsWith("-----BEGIN ") } ?: return null
        return when (marker) {
            "-----BEGIN RSA PRIVATE KEY-----" -> RSAPEMResourceKeyPairParser.INSTANCE
            "-----BEGIN DSA PRIVATE KEY-----" -> DSSPEMResourceKeyPairParser.INSTANCE
            "-----BEGIN EC PRIVATE KEY-----" -> ECDSAPEMResourceKeyPairParser.INSTANCE
            "-----BEGIN PRIVATE KEY-----" -> PKCS8PEMResourceKeyPairParser.INSTANCE
            "-----BEGIN OPENSSH PRIVATE KEY-----" -> OpenSSHKeyPairResourceParser.INSTANCE
            else -> null
        }
    }

    /**
     * Stores a private key under its default name, replacing one already there.
     *
     * The name comes from the bytes when they say what kind of key it is, and from
     * [fallbackName] otherwise — the name the file was picked under, which is what
     * OpenSSH would have called it. A file that yields neither is not a key this app
     * can hold, and is said so rather than stored under a name nothing will look for.
     */
    fun importIdentity(context: Context, bytes: ByteArray, fallbackName: String?): String {
        val name = identityNameFor(bytes, fallbackName)
            ?: error("That file is not a private key this app can name.")
        val file = File(directory(context), name)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return name
    }

    /** Takes an identity back out. A name from outside [IDENTITY_NAMES] deletes nothing. */
    fun removeIdentity(context: Context, name: String) {
        if (name !in IDENTITY_NAMES) return
        File(directory(context), name).delete()
    }

    /**
     * The identity files in `/sdcard/.ssh`, where a terminal would have left them.
     *
     * Read only, and only when all files access lets it be read: the transport itself
     * never looks here, so this exists to offer what is found rather than to use it
     * in place.
     */
    fun externalIdentities(context: Context): List<File> {
        if (!ExternalStorage.isPermitted(context)) return emptyList()
        val folder = File(Environment.getExternalStorageDirectory(), ".ssh")
        return IDENTITY_NAMES.map { File(folder, it) }.filter { it.isFile }
    }

    /** Which default name these bytes belong under, with [fallbackName] to fall back on. */
    fun identityNameFor(bytes: ByteArray, fallbackName: String?): String? =
        defaultNameFor(bytes) ?: fallbackName?.takeIf { it in IDENTITY_NAMES }

    /**
     * Which default name the bytes of a private key say they are, when they say.
     *
     * PEM and OpenSSH files carry their kind in the banner or the key type inside the
     * payload; PKCS#8 wraps it as an object identifier. A file that says nothing —
     * an encrypted key whose body is unreadable, say — gets null and has to be named
     * some other way.
     */
    fun defaultNameFor(bytes: ByteArray): String? {
        val text = String(bytes, Charsets.ISO_8859_1)
        if ("RSA PRIVATE KEY" in text) return "id_rsa"
        if ("DSA PRIVATE KEY" in text) return "id_dsa"
        if ("EC PRIVATE KEY" in text) return "id_ecdsa"
        if ("PRIVATE KEY" !in text) return null
        val body = text.lineSequence().filterNot { it.startsWith("-----") }.joinToString("")
        val decoded = runCatching { Base64.getDecoder().decode(body) }.getOrNull() ?: return null
        val inside = String(decoded, Charsets.ISO_8859_1)
        if ("ssh-ed25519" in inside) return "id_ed25519"
        if ("ssh-rsa" in inside) return "id_rsa"
        if ("ecdsa-sha2-" in inside) return "id_ecdsa"
        if ("ssh-dss" in inside) return "id_dsa"
        if (decoded.hasSubsequence(ED25519_OID)) return "id_ed25519"
        if (decoded.hasSubsequence(ECDSA_OID)) return "id_ecdsa"
        if (decoded.hasSubsequence(RSA_OID)) return "id_rsa"
        return null
    }

    /** id-ed25519, as it stands inside a PKCS#8 structure. */
    private val ED25519_OID = byteArrayOf(0x06, 0x03, 0x2B, 0x65, 0x70)

    /** id-ecPublicKey, as it stands inside a PKCS#8 structure. */
    private val ECDSA_OID =
        byteArrayOf(0x06, 0x07, 0x2A.toByte(), 0x86.toByte(), 0x48, 0xCE.toByte(), 0x3D, 0x02, 0x01)

    /** rsaEncryption, as it stands inside a PKCS#8 structure. */
    private val RSA_OID = byteArrayOf(
        0x06, 0x09, 0x2A.toByte(), 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(),
        0x0D, 0x01, 0x01, 0x01,
    )

    private fun ByteArray.hasSubsequence(needle: ByteArray): Boolean =
        indices.any { start -> start + needle.size <= size && needle.indices.all { this[start + it] == needle[it] } }
}
