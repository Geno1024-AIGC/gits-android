package com.geno1024.ai.gits.ui.repo

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** What a line of a patch is doing there, which is what decides how it is drawn. */
internal enum class DiffLineKind {
    /** The file the lines below belong to. */
    FILE,

    /** The `@@` line saying which stretch of that file is being shown. */
    HUNK,

    /** A line the commit added. */
    ADDED,

    /** A line the commit took away. */
    REMOVED,

    /** A line left as it was. */
    CONTEXT,

    /** Something said about the file rather than about one of its lines. */
    META,
}

/** One drawn line of a patch: the marker is kept on an added or a removed line. */
internal data class DiffLine(val kind: DiffLineKind, val text: String)

/**
 * Reads a unified patch into the lines a screen can draw.
 *
 * The file's own paperwork — the blob hashes and the `---`/`+++` pair naming the same
 * file twice — is dropped rather than drawn, because the name is already shown as a
 * heading. Everything else survives, including the lines saying a file is new, gone,
 * renamed or binary: none of that is in the hunks, and all of it is the answer to what
 * a commit did.
 */
internal fun parsePatch(patch: String): List<DiffLine> {
    if (patch.isBlank()) return emptyList()
    val lines = mutableListOf<DiffLine>()
    var inHeader = false
    // The patch ends with the newline of its last line, which is not a line of it.
    for (raw in patch.trimEnd('\n').lineSequence()) {
        when {
            raw.startsWith(DIFF_HEADER) -> {
                inHeader = true
                lines += DiffLine(DiffLineKind.FILE, fileNameOf(raw))
            }

            raw.startsWith("@@ ") -> {
                inHeader = false
                lines += DiffLine(DiffLineKind.HUNK, raw)
            }

            inHeader && raw.isHeaderNoise() -> Unit

            inHeader -> lines += DiffLine(DiffLineKind.META, raw)

            raw.startsWith("+") -> lines += DiffLine(DiffLineKind.ADDED, raw)

            raw.startsWith("-") -> lines += DiffLine(DiffLineKind.REMOVED, raw)

            raw.startsWith(" ") -> lines += DiffLine(DiffLineKind.CONTEXT, raw.substring(1))

            // A line that was already there, arriving without its space marker, is
            // still a line that was already there.
            raw.isEmpty() -> lines += DiffLine(DiffLineKind.CONTEXT, raw)

            else -> lines += DiffLine(DiffLineKind.META, raw)
        }
    }
    return lines
}

private const val DIFF_HEADER = "diff --git "

/** What the header says about the file that the heading has already said. */
private fun String.isHeaderNoise(): Boolean =
    startsWith("index ") ||
        startsWith("--- ") ||
        startsWith("+++ ") ||
        startsWith("similarity index ") ||
        startsWith("dissimilarity index ")

/** The path out of `diff --git a/one b/two`, taking the second of the two. */
private fun fileNameOf(header: String): String {
    val body = header.removePrefix(DIFF_HEADER)
    val second = body.substringAfterLast(" b/", missingDelimiterValue = "")
    if (second.isNotEmpty()) return second.trim('"')
    return body.substringAfterLast(" a/", missingDelimiterValue = body).trim('"')
}

/**
 * The patch as a screen of colours rather than a screen of symbols.
 *
 * Each line is composed for itself because a patch may be half a megabyte of them, and
 * a column that held all of it at once would be drawing most of what nobody scrolled
 * to. The raw form stays one tap away for reading it as git wrote it.
 */
@Composable
internal fun RenderedPatch(lines: List<DiffLine>, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val added = if (dark) Color(0xFF12301B) else Color(0xFFDCF3E2)
    val removed = if (dark) Color(0xFF33191A) else Color(0xFFFBE4E4)
    val addedText = if (dark) Color(0xFFA5D6A7) else Color(0xFF1B5E20)
    val removedText = if (dark) Color(0xFFEF9A9A) else Color(0xFF8E1B1B)

    LazyColumn(modifier = modifier) {
        itemsIndexed(lines, key = { index, _ -> index }) { _, line ->
            val drawn = when (line.kind) {
                DiffLineKind.FILE -> Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 4.dp)

                DiffLineKind.HUNK,
                DiffLineKind.CONTEXT,
                DiffLineKind.META,
                -> Modifier.fillMaxWidth()

                DiffLineKind.ADDED -> Modifier.fillMaxWidth().background(added)

                DiffLineKind.REMOVED -> Modifier.fillMaxWidth().background(removed)
            }
            Text(
                text = line.text,
                modifier = when (line.kind) {
                    DiffLineKind.ADDED, DiffLineKind.REMOVED -> drawn.padding(horizontal = 4.dp)
                    else -> drawn
                },
                style = when (line.kind) {
                    DiffLineKind.FILE -> MaterialTheme.typography.titleSmall
                    else -> MaterialTheme.typography.bodySmall
                },
                fontFamily = FontFamily.Monospace,
                fontWeight = if (line.kind == DiffLineKind.FILE) FontWeight.Bold else null,
                color = when (line.kind) {
                    DiffLineKind.FILE -> MaterialTheme.colorScheme.primary
                    DiffLineKind.ADDED -> addedText
                    DiffLineKind.REMOVED -> removedText
                    DiffLineKind.CONTEXT -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
