package g.gits.openpgp

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Provider

/**
 * BouncyCastle provider handle used for every OpenPGP operation.
 *
 * Android ships a stripped `BC` provider that contains no `org.bouncycastle.openpgp`
 * classes at all, and the JCA refuses to register a second provider under a name that
 * is already taken, so `Security.insertProviderAt(new BouncyCastleProvider(), 1)`
 * returns `-1` and leaves the platform provider in place. Resolving providers by name
 * therefore silently yields the stripped one and OpenPGP operations fail.
 *
 * Every builder in this module is handed [provider] as an object rather than by name,
 * which bypasses name-based resolution entirely. The platform provider is never
 * touched, so framework internals that legitimately rely on it keep working.
 */
object BouncyCastle {
    val provider: Provider = BouncyCastleProvider()
}
