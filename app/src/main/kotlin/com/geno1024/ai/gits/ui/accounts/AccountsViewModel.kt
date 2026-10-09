package com.geno1024.ai.gits.ui.accounts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geno1024.ai.gits.data.CredentialStore
import com.geno1024.ai.gits.data.StoredAccount
import com.geno1024.ai.gits.git.toCredentialHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AccountsUiState(
    val accounts: List<StoredAccount> = emptyList(),
    val error: String? = null,
)

/**
 * The hosts this app is signed in to, and the secrets it holds for them.
 *
 * What git would call a credential helper: one entry per host, holding the name and
 * the token the transport will offer when that host asks. Keyed by host because that
 * is the boundary the transport accepts them within — a repository may have any
 * number of remotes, and each one asks on its own host's behalf.
 */
class AccountsViewModel(application: Application) : AndroidViewModel(application) {

    private val credentialStore = CredentialStore.getInstance(application)

    private val state = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        val accounts = withContext(Dispatchers.IO) { credentialStore.accounts().sortedBy { it.host } }
        state.update { it.copy(accounts = accounts) }
    }

    /**
     * Keeps a name and a secret for a host.
     *
     * The field takes an address rather than a bare host, because an address is what
     * the user has in front of them — and the entry has to be filed under the name
     * the transport will ask for, or the secret sits somewhere nothing looks for it.
     * A bare host, a host and a port, a host and a path all reach the same answer;
     * something with no host in it at all, such as a local path, is not stored.
     */
    fun add(address: String, username: String, token: CharArray) =
        run("Could not save the account") {
            val host = address.toCredentialHost()
            if (host.isEmpty()) error("That address has no host in it.")
            credentialStore.remember(host, username.trim(), token)
        }

    fun remove(host: String) = run("Could not remove the account") { credentialStore.forget(host) }

    /** Lets every stored account go at once, for when a machine is handed over. */
    fun forgetAll() = run("Could not sign out") { credentialStore.forgetAll() }

    fun dismissError() = state.update { it.copy(error = null) }

    private fun run(prefix: String, block: () -> Unit) = viewModelScope.launch {
        state.update { it.copy(error = null) }
        val outcome = withContext(Dispatchers.IO) { runCatching { block() } }
        outcome.onFailure { failure ->
            state.update { it.copy(error = failure.message?.let { m -> "$prefix: $m" } ?: prefix) }
        }
        refresh()
    }
}
