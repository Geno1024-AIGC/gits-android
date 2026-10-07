package com.geno1024.ai.gits.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.geno1024.ai.gits.BuildConfig
import com.geno1024.ai.gits.R
import com.geno1024.ai.gits.ui.theme.MonoFontFamily
import com.geno1024.ai.gits.ui.update.Updates

/**
 * The app showing itself: the drawing it has on a launcher, the name and build it
 * answers to, whether a newer build waits, and the words it uses for git's parts.
 *
 * The glossary is here rather than in a help article because it is the one thing a
 * reader of a Chinese interface most needs explained: two serious references exist,
 * they disagree, and this app picked one of them. Showing both costs one screen and
 * answers why a familiar term is written the way it is.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
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
            AboutHero()
            HorizontalDivider()

            Updates()

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
                    chosen = true,
                    shared = entry.agreed,
                )
            }
            HorizontalDivider()
        }
    }
}

/**
 * The launcher's own drawing, then the name and the build under it — the three
 * things a person checks first when they open an about page.
 */
@Composable
private fun AboutHero() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // A launcher shows the middle 72 of the icon's 108, so the whole drawing is
        // scaled up by 108 / 72 and the disc clips it to the circle it was measured
        // in — what is here is what a round launcher mask shows, at a larger size.
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(colorResource(R.color.ic_launcher_background)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(132.dp),
            )
        }
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = BuildConfig.VERSION_NAME,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = MonoFontFamily,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The two colours a highlighted cell is drawn in, the same in both modes.
 *
 * The theme's own surface colours would put light text over a pale green in the
 * dark mode, so a highlighted cell brings its own pair instead: paper and dark
 * ink, wherever the rest of the screen is.
 */
private val HighlightBackground = Color(0xFFDCFCE7)
private val HighlightText = Color(0xFF14532D)

/**
 * One row of the table. [chosen] marks the column this app writes from, and
 * [shared] marks a reference that reads the same as it does.
 */
@Composable
private fun GlossaryRow(
    english: String,
    git: String,
    proGit2: String,
    isHeader: Boolean,
    chosen: Boolean = false,
    shared: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (isHeader) 12.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        GlossaryCell(text = english, weight = 1.1f, isHeader = isHeader)
        GlossaryCell(text = git, weight = 1f, isHeader = isHeader, highlight = chosen)
        GlossaryCell(text = proGit2, weight = 1.4f, isHeader = isHeader, highlight = shared)
    }
}

/**
 * One of the three columns. The Pro Git 2 column is the widest because its glossary
 * lists alternatives, and a cell that wraps has to keep the row it belongs to.
 *
 * Every cell is padded alike whether or not it is highlighted, so a column of
 * marks lines up with the column of text around it.
 */
@Composable
private fun RowScope.GlossaryCell(
    text: String,
    weight: Float,
    isHeader: Boolean,
    highlight: Boolean = false,
) {
    Text(
        text = text,
        style = if (isHeader) {
            MaterialTheme.typography.labelLarge
        } else {
            MaterialTheme.typography.bodySmall
        },
        color = when {
            highlight -> HighlightText
            isHeader -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .weight(weight)
            .then(
                if (highlight) {
                    Modifier.background(HighlightBackground, RoundedCornerShape(4.dp))
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}
