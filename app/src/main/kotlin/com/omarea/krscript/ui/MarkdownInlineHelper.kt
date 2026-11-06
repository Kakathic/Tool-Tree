package com.omarea.krscript.ui

object MarkdownInlineHelper {

    enum class MarkdownSpanType { BOLD, ITALIC, STRIKETHROUGH, CODE, LINK, COLOR, HIGHLIGHT, UNDERLINE, SUPERSCRIPT, SUBSCRIPT }

    data class MarkdownSpanInfo(
        val start: Int,
        val end: Int,
        val type: MarkdownSpanType,
        val href: String = ""
    )

    private const val ESCAPABLE = "\\`*_{}[]()#+-.!~<=^$"

    private val LINK_TITLE = Regex("^(\\S+)\\s+(?:\"[^\"]*\"|'[^']*')$")

    private val AUTOLINK = Regex("^(?:https?://|mailto:)\\S+$", RegexOption.IGNORE_CASE)
    private val EMAIL = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
    private const val IMAGE_PREFIX = "\uD83D\uDDBC "

    private val HTML_TAGS = mapOf(
        "u" to listOf(MarkdownSpanType.UNDERLINE),
        "mark" to listOf(MarkdownSpanType.HIGHLIGHT),
        "sup" to listOf(MarkdownSpanType.SUPERSCRIPT),
        "sub" to listOf(MarkdownSpanType.SUBSCRIPT),
        "b" to listOf(MarkdownSpanType.BOLD),
        "strong" to listOf(MarkdownSpanType.BOLD),
        "i" to listOf(MarkdownSpanType.ITALIC),
        "em" to listOf(MarkdownSpanType.ITALIC),
        "s" to listOf(MarkdownSpanType.STRIKETHROUGH),
        "del" to listOf(MarkdownSpanType.STRIKETHROUGH),
        "strike" to listOf(MarkdownSpanType.STRIKETHROUGH)
    )

    private fun findCloseParen(raw: String, from: Int): Int {
        var depth = 0
        var k = from
        while (k < raw.length) {
            val ch = raw[k]
            if (ch == '\\' && k + 1 < raw.length) {
                k += 2
                continue
            }
            if (ch == '(') depth++
            else if (ch == ')') {
                if (depth == 0) return k
                depth--
            }
            k++
        }
        return raw.indexOf(')', from)
    }

    private fun noSpace(text: String): Boolean = text.isNotEmpty() && text.none { it.isWhitespace() }

    private fun cleanHref(raw: String): String {
        var h = raw
        val m = LINK_TITLE.matchEntire(h)
        if (m != null) h = m.groupValues[1]
        if (h.length > 2 && h.startsWith("<") && h.endsWith(">")) h = h.substring(1, h.length - 1)
        return h
    }

    private fun canOpen(raw: String, i: Int, len: Int, strict: Boolean): Boolean {
        val ch = raw[i]
        val after = i + len
        if (after >= raw.length) return false
        if (ch == '_' && i > 0 && raw[i - 1].isLetterOrDigit()) return false
        if (strict) {
            if (raw[after].isWhitespace()) return false
            if (len == 1 && raw[after] == ch) return false
        }
        return true
    }

    private fun findClose(raw: String, marker: String, from: Int, strict: Boolean): Int {
        val ch = marker[0]
        val len = marker.length
        var pos = from
        while (pos < raw.length) {
            val end = raw.indexOf(marker, pos)
            if (end < 0) return -1
            pos = end + 1
            var ok = true
            if (strict) {
                ok = end > from && !raw[end - 1].isWhitespace()
                if (ok && len == 1 && (raw[end - 1] == ch || (end + 1 < raw.length && raw[end + 1] == ch))) ok = false
                if (ok && ch == '_' && end + len < raw.length && raw[end + len].isLetterOrDigit()) ok = false
            }
            if (ok) return end
        }
        return -1
    }

    fun parse(raw: String): Pair<String, List<MarkdownSpanInfo>> {
        val output = StringBuilder()
        val spans = ArrayList<MarkdownSpanInfo>()
        parseInto(raw, output, spans)
        return output.toString() to spans
    }

    private fun parseInto(raw: String, output: StringBuilder, spans: MutableList<MarkdownSpanInfo>) {
        var i = 0
        val n = raw.length
        while (i < n) {
            val c = raw[i]

            if (c == '\\' && i + 1 < n && ESCAPABLE.indexOf(raw[i + 1]) >= 0) {
                output.append(raw[i + 1])
                i += 2
                continue
            }

            if (c == '`' && i + 2 < n && raw[i + 1] == '`' && raw[i + 2] == '`') {
                val end = raw.indexOf("```", i + 3)
                if (end > i) {
                    var inner = raw.substring(i + 3, end)
                    var radius = ""
                    val barIndex = inner.lastIndexOf('|')
                    if (barIndex >= 0 && inner.substring(barIndex + 1).toFloatOrNull() != null) {
                        radius = inner.substring(barIndex + 1)
                        inner = inner.substring(0, barIndex)
                    }
                    val start = output.length
                    output.append(inner)
                    if (output.length > start) {
                        spans.add(MarkdownSpanInfo(start, output.length, MarkdownSpanType.CODE, radius))
                    }
                    i = end + 3
                    continue
                }
            }

            if (c == '`' && i + 2 < n && raw[i + 1] == '`' && raw[i + 2] != '`') {
                val end = raw.indexOf("``", i + 2)
                if (end >= 0) {
                    var inner = raw.substring(i + 2, end)
                    var radius = ""
                    val barIndex = inner.lastIndexOf('|')
                    if (barIndex >= 0 && inner.substring(barIndex + 1).toFloatOrNull() != null) {
                        radius = inner.substring(barIndex + 1)
                        inner = inner.substring(0, barIndex)
                    }
                    if (inner.length > 2 && inner.startsWith(" ") && inner.endsWith(" ") && inner.isNotBlank()) {
                        inner = inner.substring(1, inner.length - 1)
                    }
                    val start = output.length
                    output.append(inner)
                    if (output.length > start) {
                        spans.add(MarkdownSpanInfo(start, output.length, MarkdownSpanType.CODE, radius))
                    }
                    i = end + 2
                    continue
                }
            }

            if (c == '`') {
                val end = raw.indexOf('`', i + 1)
                if (end > i) {
                    var inner = raw.substring(i + 1, end)
                    var radius = ""
                    val barIndex = inner.lastIndexOf('|')
                    if (barIndex >= 0 && inner.substring(barIndex + 1).toFloatOrNull() != null) {
                        radius = inner.substring(barIndex + 1)
                        inner = inner.substring(0, barIndex)
                    }
                    val start = output.length
                    parseInto(inner, output, spans)
                    if (output.length > start) {
                        spans.add(MarkdownSpanInfo(start, output.length, MarkdownSpanType.CODE, radius))
                    }
                    i = end + 1
                    continue
                }
            }

            if ((c == '*' || c == '_') && i + 2 < n && raw[i + 1] == c && raw[i + 2] == c && canOpen(raw, i, 3, true)) {
                val end = findClose(raw, raw.substring(i, i + 3), i + 3, true)
                if (end > i) {
                    val inner = raw.substring(i + 3, end)
                    val start = output.length
                    parseInto(inner, output, spans)
                    if (output.length > start) {
                        spans.add(MarkdownSpanInfo(start, output.length, MarkdownSpanType.BOLD))
                        spans.add(MarkdownSpanInfo(start, output.length, MarkdownSpanType.ITALIC))
                    }
                    i = end + 3
                    continue
                }
            }

            if ((c == '*' || c == '_') && i + 1 < n && raw[i + 1] == c && canOpen(raw, i, 2, false)) {
                val marker = raw.substring(i, i + 2)
                val end = findClose(raw, marker, i + 2, false)
                if (end > i) {
                    val inner = raw.substring(i + 2, end)
                    val start = output.length
                    parseInto(inner, output, spans)
                    if (output.length > start) {
                        spans.add(MarkdownSpanInfo(start, output.length, MarkdownSpanType.BOLD))
                    }
                    i = end + 2
                    continue
                }
            }

            if (c == '~' && i + 1 < n && raw[i + 1] == '~') {
                val end = raw.indexOf("~~", i + 2)
                if (end > i) {
                    val inner = raw.substring(i + 2, end)
                    val start = output.length
                    parseInto(inner, output, spans)
                    if (output.length > start) {
                        spans.add(MarkdownSpanInfo(start, output.length, MarkdownSpanType.STRIKETHROUGH))
                    }
                    i = end + 2
                    continue
                }
            }

            if ((c == '*' || c == '_') && canOpen(raw, i, 1, true)) {
                val end = findClose(raw, c.toString(), i + 1, true)
                if (end > i + 1) {
                    val inner = raw.substring(i + 1, end)
                    val start = output.length
                    parseInto(inner, output, spans)
                    if (output.length > start) {
                        spans.add(MarkdownSpanInfo(start, output.length, MarkdownSpanType.ITALIC))
                    }
                    i = end + 1
                    continue
                }
            }

            if (c == '=' && i + 3 < n && raw[i + 1] == '=' && raw[i + 2] != '=' && !raw[i + 2].isWhitespace()) {
                val end = raw.indexOf("==", i + 2)
                if (end > i + 2 && !raw[end - 1].isWhitespace()) {
                    val start = output.length
                    parseInto(raw.substring(i + 2, end), output, spans)
                    if (output.length > start) {
                        spans.add(MarkdownSpanInfo(start, output.length, MarkdownSpanType.HIGHLIGHT))
                    }
                    i = end + 2
                    continue
                }
            }

            if (c == '^' || (c == '~' && i + 1 < n && raw[i + 1] != '~' && (i == 0 || raw[i - 1] != '~'))) {
                val end = raw.indexOf(c, i + 1)
                if (end > i + 1 && end - i <= 24 && noSpace(raw.substring(i + 1, end))) {
                    val start = output.length
                    parseInto(raw.substring(i + 1, end), output, spans)
                    if (output.length > start) {
                        spans.add(MarkdownSpanInfo(start, output.length, if (c == '^') MarkdownSpanType.SUPERSCRIPT else MarkdownSpanType.SUBSCRIPT))
                    }
                    i = end + 1
                    continue
                }
            }

            if (c == '<') {
                val gt = raw.indexOf('>', i + 1)
                if (gt > i + 1 && gt - i <= 200) {
                    val inner = raw.substring(i + 1, gt)
                    val link = when {
                        AUTOLINK.matches(inner) -> inner
                        EMAIL.matches(inner) -> "mailto:$inner"
                        else -> null
                    }
                    if (link != null) {
                        val start = output.length
                        output.append(inner)
                        spans.add(MarkdownSpanInfo(start, output.length, MarkdownSpanType.LINK, link))
                        i = gt + 1
                        continue
                    }
                    val tag = inner.trim().trimEnd('/').trim().lowercase()
                    if (tag == "br") {
                        output.append('\n')
                        i = gt + 1
                        continue
                    }
                    val types = HTML_TAGS[tag]
                    if (types != null && inner.trim() == tag) {
                        val closeTag = "</$tag>"
                        val close = raw.indexOf(closeTag, gt + 1, ignoreCase = true)
                        if (close >= 0) {
                            val start = output.length
                            parseInto(raw.substring(gt + 1, close), output, spans)
                            if (output.length > start) {
                                for (t in types) spans.add(MarkdownSpanInfo(start, output.length, t))
                            }
                            i = close + closeTag.length
                            continue
                        }
                    }
                }
            }

            if (c == '!' && i + 1 < n && raw[i + 1] == '[') {
                val closeBracket = raw.indexOf(']', i + 2)
                if (closeBracket > i && closeBracket + 1 < n && raw[closeBracket + 1] == '(') {
                    val closeParen = findCloseParen(raw, closeBracket + 2)
                    if (closeParen > closeBracket) {
                        val alt = raw.substring(i + 2, closeBracket).ifBlank { "image" }
                        val href = cleanHref(raw.substring(closeBracket + 2, closeParen).trim())
                        if (href.isNotEmpty()) {
                            val start = output.length
                            output.append(IMAGE_PREFIX)
                            parseInto(alt, output, spans)
                            spans.add(MarkdownSpanInfo(start, output.length, MarkdownSpanType.LINK, href))
                            i = closeParen + 1
                            continue
                        }
                    }
                }
            }

            if (c == '[') {
                val closeBracket = raw.indexOf(']', i + 1)
                if (closeBracket > i && closeBracket + 1 < n && raw[closeBracket + 1] == '(') {
                    val closeParen = findCloseParen(raw, closeBracket + 2)
                    if (closeParen > closeBracket) {
                        val label = raw.substring(i + 1, closeBracket)
                        val href = cleanHref(raw.substring(closeBracket + 2, closeParen).trim())
                        val start = output.length
                        parseInto(label, output, spans)
                        if (output.length > start && href.isNotEmpty()) {
                            spans.add(MarkdownSpanInfo(start, output.length, MarkdownSpanType.LINK, href))
                        }
                        i = closeParen + 1
                        continue
                    }
                }
            }

            if (c == '{') {
                val closeBrace = raw.indexOf('}', i + 1)
                if (closeBrace > i && closeBrace + 1 < n && raw[closeBrace + 1] == '(') {
                    val closeParen = raw.indexOf(')', closeBrace + 2)
                    if (closeParen > closeBrace) {
                        val label = raw.substring(i + 1, closeBrace)
                        val colorValue = raw.substring(closeBrace + 2, closeParen).trim()
                        val start = output.length
                        parseInto(label, output, spans)
                        if (output.length > start && colorValue.isNotEmpty()) {
                            spans.add(MarkdownSpanInfo(start, output.length, MarkdownSpanType.COLOR, colorValue))
                        }
                        i = closeParen + 1
                        continue
                    }
                }
            }

            output.append(c)
            i += 1
        }
    }
}
