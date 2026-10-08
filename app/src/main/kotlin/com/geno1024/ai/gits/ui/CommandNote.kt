package com.geno1024.ai.gits.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import com.geno1024.ai.gits.ui.theme.MonoFontFamily

/**
 * The command or option a label stands for, set small and italic at the right end of
 * whatever it annotates — the corner a reader reaches after the label, where the
 * machine's answer to "what does this actually run" sits without interrupting the
 * sentence above it.
 */
@Composable
fun CommandNote(text: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    Text(
        text = text,
        modifier = modifier,
        textAlign = TextAlign.End,
        fontStyle = FontStyle.Italic,
        fontFamily = MonoFontFamily,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A label with its command set below it, to the right — for buttons and menu items
 * where the words are for the reader and the command is for checking.
 */
@Composable
fun CommandLabel(label: String, command: String) {
    Column {
        Text(label)
        CommandNote(command, Modifier.align(Alignment.End))
    }
}
