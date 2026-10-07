package com.geno1024.ai.gits.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.geno1024.ai.gits.BuildConfig
import com.geno1024.ai.gits.R

/**
 * Who made this, what build it is, where a newer one comes from, and the words it
 * uses for git's parts.
 *
 * The glossary is here rather than in a help article because it is the one thing a
 * reader of a Chinese interface most needs explained: two serious references exist,
 * they disagree, and this app picked one of them. Showing both costs one screen and
 * answers why a familiar term is written the way it is.
 */
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenUpdates: () -> Unit) {
    SettingsScaffold(
        title = stringResource(R.string.about_title),
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            AboutField(
                label = stringResource(R.string.about_name),
                value = stringResource(R.string.app_name),
            )
            AboutField(
                label = stringResource(R.string.about_version),
                value = BuildConfig.VERSION_NAME,
            )

            HorizontalDivider()
            SettingsRow(
                icon = Icons.Default.SystemUpdate,
                title = stringResource(R.string.update_title),
                subtitle = stringResource(R.string.settings_update_subtitle),
                onClick = onOpenUpdates,
            )
            HorizontalDivider()

            SectionHeader(stringResource(R.string.about_terms))
            Text(
                text = stringResource(R.string.about_terms_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            )

            GlossaryRow(
                english = stringResource(R.string.about_col_term),
                git = stringResource(R.string.about_col_git),
                proGit2 = stringResource(R.string.about_col_progit),
                isHeader = true,
            )
            GLOSSARY.forEach { entry ->
                HorizontalDivider()
                GlossaryRow(
                    english = entry.english,
                    git = entry.git,
                    proGit2 = entry.proGit2,
                    isHeader = false,
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun AboutField(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.6f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun GlossaryRow(english: String, git: String, proGit2: String, isHeader: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (isHeader) 12.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        GlossaryCell(text = english, weight = 1.1f, isHeader = isHeader)
        GlossaryCell(text = git, weight = 1f, isHeader = isHeader)
        GlossaryCell(text = proGit2, weight = 1.4f, isHeader = isHeader)
    }
}

/**
 * One of the three columns. The Pro Git 2 column is the widest because its glossary
 * lists alternatives, and a cell that wraps has to keep the row it belongs to.
 */
@Composable
private fun RowScope.GlossaryCell(text: String, weight: Float, isHeader: Boolean) {
    Text(
        text = text,
        style = if (isHeader) {
            MaterialTheme.typography.labelLarge
        } else {
            MaterialTheme.typography.bodySmall
        },
        color = if (isHeader) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier.weight(weight),
    )
}
