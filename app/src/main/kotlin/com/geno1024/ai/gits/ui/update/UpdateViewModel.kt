package com.geno1024.ai.gits.ui.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geno1024.ai.gits.data.AppSettings
import com.geno1024.ai.gits.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UpdateUiState(
    val installed: String = "",
    val source: Updater.Source = Updater.SOURCES.first(),
    val available: Updater.Release? = null,
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val downloaded: File? = null,
    val lastInstallOutcome: String? = null,
    val error: String? = null,
    val message: String? = null,
)

/**
 * Checks for a newer build and fetches it.
 *
 * The installed version is read from the package manager rather than kept anywhere of
 * our own, so it cannot drift from what is actually running.
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<Application>()
    private val settings = AppSettings.of(app)

    private val state = MutableStateFlow(
        UpdateUiState(
            installed = installedVersion(),
            source = settings.updateSource,
            lastInstallOutcome = settings.lastInstallOutcome,
        ),
    )
    val uiState: StateFlow<UpdateUiState> = state.asStateFlow()

    init {
        check()
    }

    /**
     * How long an offer stays worth showing, and how a repeat visit is treated.
     *
     * A build is announced once. After that, coming back to this screen should not
     * re-announce it, and it should not need a tap either: the offer is simply still
     * here, listed under the newest build, and a check that found nothing worth
     * announcing is a check the person never had to ask for.
     */
    private var lastCheckedAt = 0L

    private companion object {
        /** Long enough to catch a new build, short enough not to nag. */
        const val REVISIT_AFTER_MILLIS = 30 * 60 * 1000L
    }

    /**
     * Re-checks when the last one was long enough ago to have missed something.
     *
     * Called when the screen appears rather than on construction, because the ViewModel
     * is kept by the navigation entry and outlives any single visit: without this,
     * coming back after a while would show whatever was true when it was first built,
     * which is how a check ends up looking like it did nothing.
     */
    fun recheckIfStale(nowMillis: Long = System.currentTimeMillis()) {
        if (nowMillis - lastCheckedAt >= REVISIT_AFTER_MILLIS) check()
    }

    fun check() = viewModelScope.launch {
        lastCheckedAt = System.currentTimeMillis()
        state.update { it.copy(checking = true, error = null, message = null) }
        val outcome = withContext(Dispatchers.IO) { runCatching { Updater.fetchReleases() } }
        outcome
            .onSuccess { releases ->
                val current = Updater.parseVersion(installedVersion())
                val dismissed = settings.dismissedVersion
                val found = Updater.newer(current, releases)
                state.update {
                    it.copy(
                        available = found,
                        // A build the user was already told about is not news; it is
                        // still kept so it can be installed from here.
                        message = when {
                            found == null -> "This is the newest build."
                            found.version?.toString() == dismissed -> "${found.name} is ready to install."
                            else -> "${found.name} is available."
                        },
                    )
                }
            }
            .onFailure { failure ->
                state.update {
                    it.copy(error = "Could not reach the release list: ${failure.message ?: "unknown reason"}")
                }
            }
        state.update { it.copy(checking = false) }
    }

    fun setSource(source: Updater.Source) {
        settings.updateSource = source
        state.update { it.copy(source = source) }
    }

    /** Marks the offered build as seen, so it is not offered again on every visit. */
    fun dismiss() {
        state.value.available?.version?.let { settings.dismissedVersion = it.toString() }
        state.update { it.copy(available = null, message = "New build notices are off for that build.") }
    }

    fun download() = viewModelScope.launch {
        val release = state.value.available ?: return@launch
        val target = File(app.cacheDir, "update/${release.apkName}")
        state.update {
            it.copy(downloading = true, error = null, message = null, downloadedBytes = 0, totalBytes = release.apkSize)
        }
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                Updater.download(Updater.downloadUrl(state.value.source, release), target) { done, total ->
                    // The length in the header is unknown until the first bytes land;
                    // the feed's size is the better number while that is true.
                    val size = if (total > 0) total else release.apkSize
                    state.update { it.copy(downloadedBytes = done, totalBytes = size) }
                }
            }
        }
        outcome
            .onSuccess {
                state.update { it.copy(downloaded = target, message = "Downloaded ${target.name}.") }
            }
            .onFailure { failure ->
                target.delete()
                state.update {
                    it.copy(error = "Could not download the build: ${failure.message ?: "unknown reason"}")
                }
            }
        state.update { it.copy(downloading = false) }
    }

    fun dismissError() = state.update { it.copy(error = null) }

    fun dismissMessage() = state.update { it.copy(message = null) }

    /**
     * Records what the last attempt came to, on screen and in storage alike.
     *
     * Written on the way in as well as on the way out: a tap that never gets as far as
     * the platform still has to leave something behind, or the row answers a question
     * nobody asked and looks like the button it sits under does nothing.
     */
    fun noteInstallOutcome(outcome: String) {
        settings.lastInstallOutcome = outcome
        state.update { it.copy(lastInstallOutcome = outcome, message = null) }
    }

    /**
     * Picks up an outcome recorded while this screen was not open.
     *
     * A session install kills the process, so its result lands in storage rather than in
     * the state this ViewModel holds. Reading it here is what turns an install that
     * silently did nothing into one with an answer waiting on the next visit.
     */
    fun refreshInstallOutcome() {
        val stored = settings.lastInstallOutcome ?: return
        state.update { it.copy(lastInstallOutcome = stored) }
    }

    private fun installedVersion(): String = runCatching {
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        info.versionName.orEmpty()
    }.getOrDefault("")
}
