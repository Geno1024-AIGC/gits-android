package com.geno1024.ai.gits.git

import org.eclipse.jgit.revwalk.RevCommit
import java.io.ByteArrayOutputStream

/**
 * Recovers the bytes a commit's signature actually covers.
 *
 * Git does not sign the commit object it stores. It signs the commit as it would look
 * with no `gpgsig` header, then writes the signature into that header. So a commit read
 * back from disk has to have that header removed, byte for byte, before anything can be
 * checked against it.
 *
 * Getting this wrong is silent and total. A payload off by one newline verifies against
 * nothing at all, so a wrong answer here is indistinguishable from a forged signature.
 */
internal object SignedBytes {

    private const val NEWLINE = '\n'.code.toByte()

    /**
     * The header names git stores a signature under, for either object format.
     *
     * The name is followed by a space and then the value, so a line beginning with the
     * name and a space is the start of the header rather than a mention of it.
     */
    private val SIGNATURE_HEADERS = listOf("gpgsig ", "gpgsig-sha256 ")

    /**
     * The commit with its signature header taken out, or null when it is unsigned.
     *
     * The header block runs to the first empty line. A header may be folded across
     * several lines, each continuation starting with a space, and the armored signature
     * is folded exactly that way — the blank line inside the armor is stored as a line
     * holding a single space. Dropping the header therefore means dropping the header
     * line and every following line that is indented, and nothing else.
     */
    fun of(commit: RevCommit): ByteArray? {
        if (commit.rawGpgSignature == null) return null
        val raw = commit.rawBuffer ?: return null

        val out = ByteArrayOutputStream(raw.size)
        var index = 0
        var inHeader = true
        var dropping = false

        while (index < raw.size) {
            var end = index
            while (end < raw.size && raw[end] != NEWLINE) end++
            val line = raw.decodeToString(index, end)
            val hasNewline = end < raw.size

            val keep = when {
                // Past the header block: the message was signed exactly as it stands.
                !inHeader -> true

                // A folded continuation, still part of the signature being removed.
                dropping && line.startsWith(" ") -> false

                // The blank line that ends the header block. It is not part of the
                // folded header, so it stays.
                dropping && line.isEmpty() -> {
                    inHeader = false
                    true
                }

                // An unindented line, so a header of its own rather than a
                // continuation. It may be another signature header, in which case the
                // folding starts again; either way it is judged on its own merits.
                dropping -> startsSignatureHeader(line).also { dropping = it }.not()

                line.isEmpty() -> {
                    inHeader = false
                    true
                }

                startsSignatureHeader(line) -> {
                    dropping = true
                    false
                }

                else -> true
            }

            if (keep) {
                out.write(raw, index, end - index)
                if (hasNewline) out.write(NEWLINE.toInt())
            }
            index = if (hasNewline) end + 1 else end
        }
        return out.toByteArray()
    }

    private fun startsSignatureHeader(line: String) = SIGNATURE_HEADERS.any { line.startsWith(it) }
}
