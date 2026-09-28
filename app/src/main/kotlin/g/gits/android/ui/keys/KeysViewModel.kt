package g.gits.android.ui.keys

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import g.gits.android.data.KeyStore
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

    private val state = MutableStateFlow(KeysUiState())
    val uiState: StateFlow<KeysUiState> = state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        val keys = withContext(Dispatchers.IO) { keyStore.keys() }
        state.update { it.copy(keys = keys, canSign = keys.any(StoredKey::canSign)) }
    }

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
        super.onCleared()
        keyStore.lockAll()
    }
}
