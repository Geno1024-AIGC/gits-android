package com.geno1024.ai.gits.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.geno1024.ai.gits.R
import com.geno1024.ai.gits.data.Prompting
import com.geno1024.ai.gits.git.Answer

/**
 * Puts a question from a running exchange to the person using the app.
 *
 * What was said beside the question goes above the answer, because it is what the
 * answer is about: a host key's fingerprints are the whole reason to say no, and a
 * question whose evidence sat under the buttons would be answered before it was read.
 * Saying nothing at all is offered too, and means no — the transport reads it that
 * way, so dismissing cannot end up trusting something by accident.
 */
@Composable
fun PromptDialog(prompt: Prompting, onAnswer: (String?) -> Unit) {
    // Keyed on the question so a new one starts with an empty box rather than the
    // tail of whatever was typed for the last.
    var typed by remember(prompt) { mutableStateOf("") }
    val asked = prompt.prompt

    AlertDialog(
        onDismissRequest = { onAnswer(null) },
        title = {
            Text(
                stringResource(
                    when (prompt.kind) {
                        Answer.YES_NO -> R.string.prompt_title_trust
                        Answer.SECRET -> R.string.prompt_title_secret
                        Answer.TEXT -> R.string.prompt_title_answer
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (prompt.messages.isNotEmpty()) {
                    Text(
                        text = prompt.messages.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (prompt.kind != Answer.YES_NO) {
                    asked?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = {
                            Text(
                                asked
                                    ?: stringResource(
                                        if (prompt.kind == Answer.SECRET) {
                                            R.string.prompt_title_secret
                                        } else {
                                            R.string.prompt_title_answer
                                        },
                                    ),
                            )
                        },
                        visualTransformation = if (prompt.kind == Answer.SECRET) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            if (prompt.kind == Answer.YES_NO) {
                TextButton(onClick = { onAnswer("yes") }) {
                    Text(stringResource(R.string.action_yes))
                }
            } else {
                TextButton(onClick = { onAnswer(typed) }, enabled = typed.isNotEmpty()) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        },
        dismissButton = {
            if (prompt.kind == Answer.YES_NO) {
                TextButton(onClick = { onAnswer("no") }) {
                    Text(stringResource(R.string.action_no))
                }
            } else {
                TextButton(onClick = { onAnswer(null) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}
