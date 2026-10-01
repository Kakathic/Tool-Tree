package com.omarea.krscript.ui

object MarkdownInlineHelper {

    enum class MarkdownSpanType { BOLD, ITALIC, STRIKETHROUGH, CODE, LINK, COLOR }

    data class MarkdownSpanInfo(
        val start: Int,
        val end: Int,
        val type: MarkdownSpanType,
        val href: String = ""
    )

    private const val ESCAPABLE = "\\`*_{}[]()#+-.!~"

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

            if ((c == '*' || c == '_') && i + 1 < n && raw[i + 1] == c) {
                val marker = raw.substring(i, i + 2)
                val end = raw.indexOf(marker, i + 2)
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

            if (c == '*' || c == '_') {
                val end = raw.indexOf(c, i + 1)
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

            if (c == '[') {
                val closeBracket = raw.indexOf(']', i + 1)
                if (closeBracket > i && closeBracket + 1 < n && raw[closeBracket + 1] == '(') {
                    val closeParen = raw.indexOf(')', closeBracket + 2)
                    if (closeParen > closeBracket) {
                        val label = raw.substring(i + 1, closeBracket)
                        val href = raw.substring(closeBracket + 2, closeParen).trim()
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
