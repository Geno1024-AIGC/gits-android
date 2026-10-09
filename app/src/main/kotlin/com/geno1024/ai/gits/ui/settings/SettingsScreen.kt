package com.geno1024.ai.gits.ui.settings

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geno1024.ai.gits.BuildConfig
import com.geno1024.ai.gits.R
import com.geno1024.ai.gits.data.AppSettings
import com.geno1024.ai.gits.data.CredentialStore
import com.geno1024.ai.gits.data.IdentityStore
import com.geno1024.ai.gits.data.KeyStore
import com.geno1024.ai.gits.data.Ssh
import com.geno1024.ai.gits.data.StoredAccount
import com.geno1024.ai.gits.data.StoredKey
import com.geno1024.ai.gits.git.Identity
import com.geno1024.ai.gits.ui.CommandNote
import com.geno1024.ai.gits.ui.theme.MonoFontFamily
import com.geno1024.ai.gits.ui.theme.ThemeMode
import com.geno1024.ai.gits.ui.theme.ThemePreset
import com.geno1024.ai.gits.ui.theme.ThemeSettings
import com.geno1024.ai.gits.ui.theme.ThemeStore
import com.geno1024.ai.gits.ui.theme.isDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Everything the app is configured with that is not a repository's own settings.
 *
 * The update check lives here rather than on its own screen: it is a setting in the
 * sense that a person changes it once and forgets, and a bare icon on the home screen
 * for it gave it more prominence than a feature that runs on its own anyway.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenKeys: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenSsh: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    var editingIdentity by remember { mutableStateOf(false) }
    var editingBranch by remember { mutableStateOf(false) }
    var choosingThemeMode by remember { mutableStateOf(false) }
    var choosingPreset by remember { mutableStateOf(false) }
    var choosingColor by remember { mutableStateOf<String?>(null) }

    SettingsScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                SectionHeader(stringResource(R.string.settings_section_identity))
                SettingsCard {
                    SettingsRow(
                        icon = painterResource(R.drawable.ic_person),
                        title = stringResource(R.string.settings_identity),
                        subtitle = state.identity?.let { "${it.name} <${it.email}>" }
                            ?: stringResource(R.string.settings_identity_unset),
                        onClick = { editingIdentity = true },
                    )
                }
            }

            item {
                SectionHeader(stringResource(R.string.settings_section_credentials))
                SettingsCard {
                    SettingsRow(
                        icon = painterResource(R.drawable.ic_vpn_key),
                        title = stringResource(R.string.keys_title),
                        subtitle = when {
                            state.keys.isEmpty() -> stringResource(R.string.settings_no_keys)
                            state.selectedFingerprint == null ->
                                stringResource(R.string.settings_keys_no_selection, state.keys.size)

                            else -> stringResource(R.string.settings_keys_selected, state.keys.size)
                        },
                        onClick = onOpenKeys,
                    )
                    RowDivider()
                    SettingsRow(
                        icon = painterResource(R.drawable.ic_cloud),
                        title = stringResource(R.string.accounts_section),
                        subtitle = if (state.accounts.isEmpty()) {
                            stringResource(R.string.settings_accounts_none)
                        } else {
                            stringResource(
                                R.string.settings_accounts_selected,
                                state.accounts.size,
                                state.accounts.joinToString { it.host },
                            )
                        },
                        monoSubtitle = true,
                        onClick = onOpenAccounts,
                    )
                    RowDivider()
                    SettingsRow(
                        icon = painterResource(R.drawable.ic_terminal),
                        title = stringResource(R.string.ssh_title),
                        subtitle = if (state.sshKeys.isEmpty()) {
                            stringResource(R.string.settings_no_keys)
                        } else {
                            stringResource(R.string.settings_keys_selected, state.sshKeys.size)
                        },
                        onClick = onOpenSsh,
                    )
                }
            }

            item {
                SectionHeader(stringResource(R.string.settings_section_repositories))
                SettingsCard {
                    SettingsRow(
                        icon = painterResource(R.drawable.ic_call_split),
                        title = stringResource(R.string.settings_default_branch),
                        subtitle = state.defaultBranch,
                        monoSubtitle = true,
                        onClick = { editingBranch = true },
                    )
                }
            }

            item {
                SectionHeader(stringResource(R.string.settings_appearance))
                SettingsCard {
                    SettingsRow(
                        icon = painterResource(R.drawable.ic_dark_mode),
                        title = stringResource(R.string.settings_theme),
                        subtitle = stringResource(theme.mode.label()),
                        onClick = { choosingThemeMode = true },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = painterResource(R.drawable.ic_palette),
                        title = stringResource(R.string.settings_theme_preset),
                        subtitle = stringResource(theme.preset.label()),
                        onClick = { choosingPreset = true },
                    )
                    // The colours a custom scheme is mixed from are only worth showing
                    // while a custom scheme is the one in use.
                    if (theme.preset == ThemePreset.CUSTOM) {
                        CUSTOM_COLOR_SLOTS.forEach { (slot, name) ->
                            RowDivider()
                            SettingsRow(
                                icon = painterResource(R.drawable.ic_palette),
                                title = stringResource(name),
                                subtitle = theme.colors[slot]
                                    ?.let { colorHex(it) }
                                    ?: stringResource(R.string.theme_custom_unset),
                                swatch = theme.colors[slot],
                                monoSubtitle = true,
                                onClick = { choosingColor = slot },
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(stringResource(R.string.settings_section_app))
                SettingsCard {
                    SettingsRow(
                        icon = painterResource(R.drawable.ic_info),
                        title = stringResource(R.string.about_title),
                        subtitle = BuildConfig.VERSION_NAME,
                        monoSubtitle = true,
                        onClick = onOpenAbout,
                    )
                }
            }
        }
    }

    if (editingIdentity) {
        IdentityDialog(
            existing = state.identity,
            onDismiss = { editingIdentity = false },
            onSave = {
                viewModel.saveIdentity(it)
                editingIdentity = false
            },
        )
    }

    if (editingBranch) {
        DefaultBranchDialog(
            current = state.defaultBranch,
            onDismiss = { editingBranch = false },
            onSave = {
                viewModel.saveDefaultBranch(it)
                editingBranch = false
            },
        )
    }

    if (choosingThemeMode) {
        ThemeChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries.map { it.id to stringResource(it.label()) },
            selected = theme.mode.id,
            onSelect = { viewModel.setThemeMode(ThemeMode.from(it)) },
            onDismiss = { choosingThemeMode = false },
        )
    }

    if (choosingPreset) {
        ThemeChoiceDialog(
            title = stringResource(R.string.settings_theme_preset),
            options = ThemePreset.entries.map { it.id to stringResource(it.label()) },
            selected = theme.preset.id,
            onSelect = { viewModel.setThemePreset(ThemePreset.from(it)) },
            onDismiss = { choosingPreset = false },
        )
    }

    choosingColor?.let { slot ->
        val default = customColorDefault(slot, theme.mode.isDark())
        ColorPickerDialog(
            title = stringResource(CUSTOM_COLOR_SLOTS.first { (name, _) -> name == slot }.second),
            initial = theme.colors[slot] ?: default,
            defaultColor = default,
            onPick = { color ->
                viewModel.setThemeColor(slot, color.takeIf { it >= 0 })
                choosingColor = null
            },
            onDismiss = { choosingColor = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        content = content,
    )
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

/**
 * The sheet a section's rows sit on: one card under each header, held apart from
 * the screen by its own tone rather than by a shadow.
 */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(content = content)
    }
}

/** Between two rows, starting under their text so the icons stand clear of it. */
@Composable
private fun RowDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 68.dp))
}

/**
 * One row of a section: what it configures, then what it is currently set to,
 * the whole row a tap target. [swatch] paints the leading disc with a colour
 * instead of an icon, and [monoSubtitle] sets a value that is a name or a path
 * in the face it is read in.
 */
@Composable
fun SettingsRow(
    icon: Painter,
    title: String,
    subtitle: String,
    swatch: Long? = null,
    monoSubtitle: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (swatch == null) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        Color(swatch.toInt())
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (swatch == null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = if (monoSubtitle) MonoFontFamily else null,
            )
        }
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IdentityDialog(
    existing: Identity?,
    onDismiss: () -> Unit,
    onSave: (Identity) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var email by remember { mutableStateOf(existing?.email.orEmpty()) }
    val valid = name.isNotBlank() && email.isNotBlank() && email.contains('@')

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_identity)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    supportingText = { CommandNote("config user.name") },
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.field_email)) },
                    singleLine = true,
                    isError = email.isNotEmpty() && !email.contains('@'),
                    supportingText = { CommandNote("config user.email") },
                )
                Text(
                    text = stringResource(R.string.settings_identity_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(Identity(name.trim(), email.trim())) },
                enabled = valid,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Sets what a new repository starts on.
 *
 * Held here rather than decided at the moment of creation because it is the kind of
 * answer that should stay put: someone who works on `trunk` should not have to
 * remember to say so on every repository, and someone who does not should be able to
 * see that the app was going to.
 */
@Composable
private fun DefaultBranchDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var branch by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_default_branch)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text(stringResource(R.string.field_initial_branch)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { CommandNote("config init.defaultBranch") },
                )
                Text(
                    text = stringResource(R.string.settings_default_branch_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(branch) },
                enabled = branch.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

data class SettingsUiState(
    val identity: Identity? = null,
    val keys: List<StoredKey> = emptyList(),
    val selectedFingerprint: String? = null,
    val accounts: List<StoredAccount> = emptyList(),
    val sshKeys: List<String> = emptyList(),
    val defaultBranch: String = AppSettings.DEFAULT_BRANCH,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val identityStore = IdentityStore.getInstance(application)
    private val keyStore = KeyStore.getInstance(application)
    private val credentialStore = CredentialStore.getInstance(application)
    private val appSettings = AppSettings.of(application)
    private val themeStore = ThemeStore.getInstance(application)

    val uiState = kotlinx.coroutines.flow.MutableStateFlow(SettingsUiState())

    /**
     * The theme as it stands, kept by the store rather than rebuilt here because the
     * activity draws every screen through it: a change made on this screen has to
     * reach the activity without waiting for the screen to be asked for state.
     */
    val theme: StateFlow<ThemeSettings> = themeStore.settings

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        val (keys, sshKeys) = withContext(Dispatchers.IO) {
            keyStore.keys() to Ssh.identities(getApplication())
        }
        uiState.value = SettingsUiState(
            identity = identityStore.identity(),
            keys = keys,
            selectedFingerprint = keyStore.selected,
            accounts = credentialStore.accounts().sortedBy { it.host },
            sshKeys = sshKeys,
            defaultBranch = appSettings.initialBranch,
        )
    }

    fun saveIdentity(identity: Identity) {
        identityStore.remember(identity)
        refresh()
    }

    fun saveDefaultBranch(branch: String) {
        appSettings.initialBranch = branch
        refresh()
    }

    /** Puts the app in the light, in the dark, or on the system's answer. */
    fun setThemeMode(mode: ThemeMode) = themeStore.setMode(mode)

    /** Swaps the scheme the colours are taken from. */
    fun setThemePreset(preset: ThemePreset) = themeStore.setPreset(preset)

    /** Fills one slot of the custom scheme, or empties it so the default shows through. */
    fun setThemeColor(slot: String, color: Long?) = themeStore.setColor(slot, color)
}

/**
 * The slots a custom scheme is mixed from, each with the name it is offered under.
 *
 * The same seven the custom scheme reads when it is drawn: a slot named here but not
 * read there could be set and never show, and one read but unnamed could never be set.
 */
private val CUSTOM_COLOR_SLOTS = listOf(
    "primary" to R.string.theme_custom_primary,
    "background" to R.string.theme_custom_background,
    "surface" to R.string.theme_custom_surface,
    "onBackground" to R.string.theme_custom_on_background,
    "onSurface" to R.string.theme_custom_on_surface,
    "surfaceVariant" to R.string.theme_custom_surface_variant,
    "onSurfaceVariant" to R.string.theme_custom_on_surface_variant,
)

/** How one mode is offered in the list of them. */
private fun ThemeMode.label() = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

/** How one preset is offered in the list of them. */
private fun ThemePreset.label() = when (this) {
    ThemePreset.DEFAULT -> R.string.theme_preset_default
    ThemePreset.GITHUB -> R.string.theme_preset_github
    ThemePreset.TOKYO_NIGHT -> R.string.theme_preset_tokyo_night
    ThemePreset.DRACULA -> R.string.theme_preset_dracula
    ThemePreset.NORD -> R.string.theme_preset_nord
    ThemePreset.ONE_DARK -> R.string.theme_preset_one_dark
    ThemePreset.SOLARIZED -> R.string.theme_preset_solarized
    ThemePreset.GRUVBOX -> R.string.theme_preset_gruvbox
    ThemePreset.CATPPUCCIN -> R.string.theme_preset_catppuccin
    ThemePreset.CHINA_RED -> R.string.theme_preset_china_red
    ThemePreset.PERSIMMON -> R.string.theme_preset_persimmon
    ThemePreset.LEMON -> R.string.theme_preset_lemon
    ThemePreset.FOREST -> R.string.theme_preset_forest
    ThemePreset.MINT -> R.string.theme_preset_mint
    ThemePreset.SKY_BLUE -> R.string.theme_preset_sky_blue
    ThemePreset.GRAPE -> R.string.theme_preset_grape
    ThemePreset.CUSTOM -> R.string.theme_preset_custom
}

/** A colour as people say it out loud: six digits, no alpha, no room for the rest. */
private fun colorHex(color: Long) = "#%06X".format(color.toInt() and 0xFFFFFF)

/**
 * Offers a short list of answers and takes one back.
 *
 * The answer is held here rather than applied on the tap, so a dialog dismissed without
 * a decision leaves the setting exactly as it was.
 */
@Composable
private fun ThemeChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf(selected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { chosen = value }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = chosen == value, onClick = { chosen = value })
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSelect(chosen)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
