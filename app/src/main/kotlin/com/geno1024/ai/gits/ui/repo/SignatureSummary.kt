package com.geno1024.ai.gits.ui.repo

import com.geno1024.ai.gits.git.LogEntry
import com.geno1024.ai.gits.openpgp.SignatureCheck

/** How far a commit's signature can be trusted, as far as this app can tell. */
enum class Trust {
    /** Checked, and it checks out. */
    GOOD,

    /** Nothing was proved either way, and nothing is wrong. */
    UNKNOWN,

    /** Something is actually wrong with the signature. */
    BAD,
}

data class SignatureSummary(val trust: Trust, val text: String)

/**
 * What to say about a commit's signature.
 *
 * Null for an unsigned commit, so that stays quiet instead of being given a reassurance
 * it did not earn. The distinction the wording is careful about is between a signature
 * that failed and one nobody here was able to check: telling someone their history has
 * been tampered with when the real problem is that we lack a key would be a libel, and
 * one that quietly reads as reassurance when a key is missing is worse still.
 */
fun LogEntry.signatureSummary(): SignatureSummary? {
    if (!signaturePresent) return null

    val key = signedByKeyId ?: "an unnamed key"
    return when (val check = signatureCheck) {
        null -> SignatureSummary(Trust.UNKNOWN, "signed by $key, not checked")
        is SignatureCheck.Verified -> SignatureSummary(Trust.GOOD, "signed by $key, verified")
        is SignatureCheck.NoKey -> SignatureSummary(Trust.UNKNOWN, "signed by $key, key not held")
        is SignatureCheck.KeyNotValidThen ->
            SignatureSummary(Trust.UNKNOWN, "signed by $key, key not valid when signed")

        is SignatureCheck.Invalid -> SignatureSummary(Trust.BAD, "signed by $key, does not verify")
        is SignatureCheck.Malformed -> SignatureSummary(Trust.BAD, "signature unreadable")
    }
}
