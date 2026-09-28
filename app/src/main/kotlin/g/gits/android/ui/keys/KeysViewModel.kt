package g.gits.android.ui.keys

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import g.gits.android.data.KeyStore
import g.gits.android.data.CredentialStore
import g.gits.android.data.StoredAccount
import g.gits.android.data.StoredKey
import g.gits.openpgp.KeyAlgorithm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class KeysUiState(
    val keys: List<StoredKey> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val canSign: Boolean = false,
    val accounts: List<StoredAccount> = emptyList(),
    val selected: String? = null,
)

/**
 * The keys this app signs commits with.
 *
 * Passphrases are held in memory for the session and never written down, so keys are
 * listed whether or not they are currently unlocked and a locked key says so instead
 * of failing later at commit time.
 */
class KeysViewModel(application: Application) : AndroidViewModel(application) {

    private val keyStore = KeyStore.getInstance(application)
    private val credentials = CredentialStore.getInstance(application)

    private val state = MutableStateFlow(KeysUiState())
    val uiState: StateFlow<KeysUiState> = state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        val keys = withContext(Dispatchers.IO) { keyStore.keys() }
        val accounts = withContext(Dispatchers.IO) { credentials.accounts() }
        state.update {
            it.copy(
                keys = keys,
                canSign = keys.any(StoredKey::canSign),
                accounts = accounts,
                selected = keyStore.selected,
            )
        }
    }

    fun forgetAccount(account: StoredAccount) =
        run("Could not forget ${account.host}") { credentials.forget(account.host) }

    fun forgetAllAccounts() = run("Could not forget the accounts") { credentials.forgetAll() }

    fun generate(name: String, email: String, algorithm: KeyAlgorithm, passphrase: CharArray) =
        run("Could not create the key") { keyStore.generate("$name <$email>", algorithm, passphrase) }

    /**
     * Reads the picked file and adds its keys.
     *
     * The content is read here rather than in the screen so it happens on the IO
     * dispatcher, and so a file the provider cannot open fails as an ordinary error.
     */
    fun importFrom(uri: Uri, passphrase: CharArray) = run("Could not import the key") {
        val bytes = getApplication<Application>().contentResolver
            .openInputStream(uri)
            ?.use { it.readBytes() }
            ?: error("That file could not be opened.")
        keyStore.importArmored(bytes, passphrase)
    }

    fun select(key: StoredKey) = run("Could not use that key") { keyStore.selected = key.fingerprintHex }

    /**
     * Writes a key out to a file the user named.
     *
     * Goes through the content resolver rather than to a path, because the file may be
     * anywhere the user chose and this app has no business assuming otherwise.
     */
    fun exportTo(uri: Uri, request: ExportRequest) = run("Could not write the key") {
        val bytes = if (request.public) {
            keyStore.publicKeyOf(request.key.fingerprintHex)
        } else {
            keyStore.secretKeyOf(request.key.fingerprintHex)
        }
        getApplication<Application>().contentResolver
            .openOutputStream(uri)
            ?.use { it.write(bytes) }
            ?: error("That file could not be opened for writing.")
    }

    fun forget(key: StoredKey) = run("Could not remove the key") { keyStore.forget(key.fingerprintHex) }

    fun lockAll() = run("Could not lock the keys") { keyStore.lockAll() }

    fun dismissError() = state.update { it.copy(error = null) }

    fun dismissMessage() = state.update { it.copy(message = null) }

    private fun run(prefix: String, block: () -> Unit) = viewModelScope.launch {
        state.update { it.copy(busy = true, error = null) }
        val outcome = withContext(Dispatchers.IO) { runCatching { block() } }
        outcome
            .onSuccess { state.update { it.copy(message = "Done.") } }
            .onFailure { failure ->
                state.update { it.copy(error = failure.message?.let { m -> "$prefix: $m" } ?: prefix) }
            }
        state.update { it.copy(busy = false) }
        refresh()
    }

    override fun onCleared() {
        // Locking on the way out means an unlocked key does not outlive the screen
        // that unlocked it. Tokens stay, because they are meant to.
        keyStore.lockAll()
    }
}
