package com.geno1024.ai.gits.ui.repo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/** What a run of characters is doing, which is what decides the colour it is given. */
internal enum class TokenKind {
    /** Said about the code rather than being part of it. */
    COMMENT,

    /** Between quotes, taken as it stands whatever is inside. */
    STRING,

    /** A count. */
    NUMBER,

    /** A word the language has already spent. */
    KEYWORD,

    /** Marked with an `@`, as an annotation or a decorator is. */
    ANNOTATION,
}

/** A run of the source from [start] up to, but not including, [end]. */
internal data class TokenSpan(val start: Int, val end: Int, val kind: TokenKind)

/** The file families this app colours, told apart by the extensions they answer to. */
internal enum class CodeLanguage {
    C_LIKE,
    PYTHON,
    SHELL,
    JSON,
    XML,

    /** Anything else: the words most languages share, and no more than that. */
    GENERIC,
    ;

    companion object {
        /** The family [name] belongs to; anything unrecognised is the generic one. */
        fun of(name: String): CodeLanguage {
            val file = name.substringAfterLast('/')
            if ('.' !in file) return GENERIC
            return when (file.substringAfterLast('.').lowercase()) {
                "kt", "kts", "gradle", "java", "groovy", "scala",
                "js", "jsx", "ts", "tsx",
                "c", "h", "cpp", "hpp", "cc", "cs",
                -> C_LIKE

                "py", "pyw" -> PYTHON
                "sh", "bash", "zsh" -> SHELL
                "json" -> JSON
                "xml", "html", "htm", "svg", "xsl", "xhtml" -> XML
                else -> GENERIC
            }
        }
    }
}

/** Past this many characters the file is shown as it was written, colour and all. */
internal const val MAX_HIGHLIGHT = 128 * 1024

/** Whether a file of this size is small enough for a pass over it to be free. */
internal fun worthHighlighting(content: String): Boolean = content.length <= MAX_HIGHLIGHT

/** The rules one family keeps about comments, strings and words. */
private data class Rules(
    val lineComments: List<String>,
    val blockComment: Pair<String, String>?,
    val tripleQuotes: List<String>,
    val quotes: List<Char>,
    val keywords: Set<String>,
    val annotations: Boolean,
)

private val C_LIKE_KEYWORDS = setOf(
    "abstract", "as", "break", "case", "catch", "class", "companion", "const", "constructor",
    "continue", "data", "default", "do", "else", "enum", "extends", "external", "false", "final",
    "finally", "for", "fun", "function", "get", "if", "implements", "import", "in", "instanceof",
    "interface", "internal", "is", "lateinit", "new", "null", "object", "open", "override",
    "package", "private", "protected", "public", "return", "sealed", "set", "static", "super",
    "suspend", "switch", "this", "throw", "throws", "true", "try", "typealias", "typeof", "val",
    "var", "void", "while", "with", "yield",
)

private val PYTHON_KEYWORDS = setOf(
    "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del", "elif",
    "else", "except", "False", "finally", "for", "from", "global", "if", "import", "in", "is",
    "lambda", "None", "nonlocal", "not", "or", "pass", "raise", "return", "True", "try", "while",
    "with", "yield",
)

private val SHELL_KEYWORDS = setOf(
    "case", "do", "done", "elif", "else", "esac", "exit", "fi", "for", "function", "if", "in",
    "local", "export", "read", "return", "select", "then", "time", "until", "while",
)

private val JSON_KEYWORDS = setOf("true", "false", "null")

/**
 * The words most languages have spent, for a file whose family is not known.
 *
 * A handful rather than a catalogue: only what turns up in enough languages that
 * colouring it in an unfamiliar one still reads as colouring the code.
 */
private val COMMON_KEYWORDS = setOf(
    "and", "as", "break", "case", "catch", "class", "const", "continue", "def", "do", "else",
    "end", "enum", "false", "for", "from", "fun", "function", "if", "import", "in", "interface",
    "is", "let", "match", "new", "nil", "None", "not", "null", "or", "private", "public", "return",
    "self", "static", "struct", "switch", "this", "throw", "true", "try", "typeof", "val", "var",
    "void", "while",
)

private fun rulesOf(language: CodeLanguage): Rules = when (language) {
    CodeLanguage.C_LIKE -> Rules(
        lineComments = listOf("//"),
        blockComment = "/*" to "*/",
        tripleQuotes = listOf("\"\"\""),
        quotes = listOf('"', '\''),
        keywords = C_LIKE_KEYWORDS,
        annotations = true,
    )

    CodeLanguage.PYTHON -> Rules(
        lineComments = listOf("#"),
        blockComment = null,
        tripleQuotes = listOf("\"\"\"", "'''"),
        quotes = listOf('"', '\''),
        keywords = PYTHON_KEYWORDS,
        annotations = true,
    )

    CodeLanguage.SHELL -> Rules(
        lineComments = listOf("#"),
        blockComment = null,
        tripleQuotes = emptyList(),
        quotes = listOf('"', '\''),
        keywords = SHELL_KEYWORDS,
        annotations = false,
    )

    CodeLanguage.JSON -> Rules(
        lineComments = emptyList(),
        blockComment = null,
        tripleQuotes = emptyList(),
        quotes = listOf('"'),
        keywords = JSON_KEYWORDS,
        annotations = false,
    )

    CodeLanguage.XML -> Rules(
        lineComments = emptyList(),
        blockComment = "<!--" to "-->",
        tripleQuotes = emptyList(),
        quotes = listOf('"', '\''),
        keywords = emptySet(),
        annotations = false,
    )

    CodeLanguage.GENERIC -> Rules(
        lineComments = listOf("//", "#"),
        blockComment = "/*" to "*/",
        tripleQuotes = emptyList(),
        quotes = listOf('"', '\''),
        keywords = COMMON_KEYWORDS,
        annotations = false,
    )
}

/**
 * The runs of [source] worth a colour of their own.
 *
 * One pass with the rules of [language] and nothing clever: a comment, a quoted run and
 * a word are found where they open, and everything between them is left as it was. A
 * comment opens only where one can open — outside a string, which is taken whole — so
 * a `//` inside a string is text and a `"` inside a comment is not the start of one.
 * An unterminated comment or string runs to the end of the file, which is where such
 * a thing ends.
 */
internal fun findTokens(source: String, language: CodeLanguage): List<TokenSpan> {
    val rules = rulesOf(language)
    val spans = mutableListOf<TokenSpan>()
    var i = 0
    while (i < source.length) {
        val block = rules.blockComment
        if (block != null && source.startsWith(block.first, i)) {
            val closed = source.indexOf(block.second, i + block.first.length)
            val end = if (closed < 0) source.length else closed + block.second.length
            spans += TokenSpan(i, end, TokenKind.COMMENT)
            i = end
            continue
        }

        val line = rules.lineComments.firstOrNull { source.startsWith(it, i) }
        if (line != null) {
            val newline = source.indexOf('\n', i + line.length)
            val end = if (newline < 0) source.length else newline
            spans += TokenSpan(i, end, TokenKind.COMMENT)
            i = end
            continue
        }

        val triple = rules.tripleQuotes.firstOrNull { source.startsWith(it, i) }
        val quote = triple ?: rules.quotes.firstOrNull { it == source[i] }?.toString()
        if (quote != null) {
            val end = endOfQuoted(source, i, quote)
            spans += TokenSpan(i, end, TokenKind.STRING)
            i = end
            continue
        }

        val character = source[i]
        if (rules.annotations && character == '@' && i + 1 < source.length && source[i + 1].isLetter()) {
            var end = i + 1
            while (end < source.length && (source[end].isLetterOrDigit() || source[end] == '_')) end++
            spans += TokenSpan(i, end, TokenKind.ANNOTATION)
            i = end
            continue
        }

        if (character.isLetter() || character == '_') {
            var end = i
            while (end < source.length && (source[end].isLetterOrDigit() || source[end] == '_')) end++
            if (source.substring(i, end) in rules.keywords) {
                spans += TokenSpan(i, end, TokenKind.KEYWORD)
            }
            i = end
            continue
        }

        if (character.isDigit()) {
            var end = i + 1
            while (end < source.length && source[end].inNumber()) end++
            spans += TokenSpan(i, end, TokenKind.NUMBER)
            i = end
            continue
        }

        i++
    }
    return spans
}

/** Digits, letters, dots and underscores all go on being part of one count. */
private fun Char.inNumber() = isLetterOrDigit() || this == '.' || this == '_'

/** Where a quote opened at [start] closes, or the end of the file when it never does. */
private fun endOfQuoted(source: String, start: Int, quote: String): Int {
    var i = start + quote.length
    while (i < source.length) {
        if (source[i] == '\\') {
            i += 2
            continue
        }
        if (source.startsWith(quote, i)) return i + quote.length
        i++
    }
    return source.length
}

/**
 * The file's text with the parts worth colouring coloured.
 *
 * A file of a family this app knows gets that family's words; anything else still gets
 * the comments, strings, numbers and handful of words most languages share, because a
 * mark on a familiar word in an unfamiliar file is better than no marks at all. A file
 * past [MAX_HIGHLIGHT] comes back exactly as it was: a highlight is an ornament, and
 * an ornament that makes the reader wait for the words is not worth having.
 */
@Composable
internal fun highlightedText(fileName: String, content: String): AnnotatedString {
    val colours = MaterialTheme.colorScheme.let { scheme ->
        mapOf(
            TokenKind.COMMENT to scheme.onSurfaceVariant,
            TokenKind.STRING to scheme.secondary,
            TokenKind.NUMBER to scheme.tertiary,
            TokenKind.KEYWORD to scheme.primary,
            TokenKind.ANNOTATION to scheme.tertiary,
        )
    }
    if (!worthHighlighting(content)) return AnnotatedString(content)
    val language = CodeLanguage.of(fileName)
    val spans = findTokens(content, language)
    if (spans.isEmpty()) return AnnotatedString(content)
    return buildAnnotatedString {
        append(content)
        spans.forEach { span ->
            addStyle(SpanStyle(color = colours.getValue(span.kind)), span.start, span.end)
        }
    }
}
