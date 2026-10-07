package com.geno1024.ai.gits.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File

private val MonoFontCandidates = listOf(
    "/system/fonts/CutiveMono.ttf",
    "/system/fonts/DroidSansMono.ttf",
)

/**
 * The monospaced family this app asks for, resolved against the files the device
 * actually ships.
 *
 * `FontFamily.Monospace` names a generic family whose mapping some devices get
 * wrong: a face that is not monospaced at all, or none at all. Reading the
 * candidate files here, in the order they have appeared across Android releases,
 * gives the same face on every screen regardless of what the generic resolves to,
 * and the generic stays as the last resort when neither file exists.
 */
val MonoFontFamily: FontFamily = MonoFontCandidates
    .asSequence()
    .map { File(it) }
    .firstOrNull { it.exists() }
    ?.let { file ->
        FontFamily(
            Font(file, weight = FontWeight.Normal),
            Font(file, weight = FontWeight.Bold),
        )
    }
    ?: FontFamily.Monospace
