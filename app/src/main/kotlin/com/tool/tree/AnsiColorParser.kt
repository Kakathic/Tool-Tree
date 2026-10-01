package com.tool.tree

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.util.Patterns
import java.util.regex.Pattern

object AnsiColorParser {

    private val CSI_PATTERN = Pattern.compile(
        "\\u001B\\[([0-9;:?]*)([ -/]*)([@-~])"
    )

    private val OSC_PATTERN = Pattern.compile(
        "\\u001B\\]([^\\u0007\\u001B]*)(?:\\u0007|\\u001B\\\\)"
    )

    private val SIMPLE_ESC_PATTERN = Pattern.compile(
        "\\u001B[()#][A-Za-z0-9]|\\u001B[=>]"
    )

    private const val DEFAULT_FOREGROUND = Color.LTGRAY
    private const val DEFAULT_BACKGROUND = Color.TRANSPARENT

    private var currentFgColor = DEFAULT_FOREGROUND
    private var currentBgColor = DEFAULT_BACKGROUND
    private var isBold = false
    private var isItalic = false
    private var isUnderline = false
    private var isStrikethrough = false
    private var isReverse = false
    private var isDim = false

    private val ANSI_16_COLORS = intArrayOf(
        Color.BLACK,
        Color.RED,
        Color.GREEN,
        Color.rgb(200, 150, 0),
        Color.BLUE,
        Color.MAGENTA,
        Color.CYAN,
        Color.WHITE,
        Color.DKGRAY,
        Color.rgb(255, 85, 85),
        Color.rgb(85, 255, 85),
        Color.rgb(255, 255, 85),
        Color.rgb(85, 85, 255),
        Color.rgb(255, 85, 255),
        Color.rgb(85, 255, 255),
        Color.WHITE
    )

    fun parse(text: String?): CharSequence {
        if (text == null) return ""

        val builder = SpannableStringBuilder()
        val plainBuffer = StringBuilder()

        var pendingLinkUrl: String? = null
        var pendingLinkStart = -1

        var i = 0
        val len = text.length

        fun flushPlainBuffer() {
            if (plainBuffer.isNotEmpty()) {
                appendStyledText(builder, plainBuffer.toString(), autoLink = pendingLinkUrl == null)
                plainBuffer.setLength(0)
            }
        }

        while (i < len) {
            val c = text[i]

            if (c != '\u001B') {
                plainBuffer.append(c)
                i++
                continue
            }

            flushPlainBuffer()

            val nextChar = if (i + 1 < len) text[i + 1] else '\u0000'

            when (nextChar) {
                '[' -> {
                    val m = CSI_PATTERN.matcher(text)
                    m.region(i, len)
                    if (m.lookingAt()) {
                        val params = m.group(1) ?: ""
                        val finalByte = m.group(3)
                        if (finalByte == "m") {
                            parseAnsiParameters(params)
                        }
                        i = m.end()
                    } else {
                        i++
                    }
                }

                ']' -> {
                    val m = OSC_PATTERN.matcher(text)
                    m.region(i, len)
                    if (m.lookingAt()) {
                        val body = m.group(1) ?: ""
                        if (body.startsWith("8;")) {
                            val url = body.substringAfterLast(';', "")
                            if (url.isEmpty()) {
                                if (pendingLinkUrl != null && pendingLinkStart in 0..builder.length) {
                                    builder.setSpan(
                                        URLSpan(pendingLinkUrl),
                                        pendingLinkStart,
                                        builder.length,
                                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                    )
                                }
                                pendingLinkUrl = null
                                pendingLinkStart = -1
                            } else {
                                pendingLinkUrl = url
                                pendingLinkStart = builder.length
                            }
                        }
                        i = m.end()
                    } else {
                        i++
                    }
                }

                else -> {
                    val m = SIMPLE_ESC_PATTERN.matcher(text)
                    m.region(i, len)
                    if (m.lookingAt()) {
                        i = m.end()
                    } else {
                        i++
                    }
                }
            }
        }

        flushPlainBuffer()

        if (pendingLinkUrl != null && pendingLinkStart in 0 until builder.length) {
            builder.setSpan(
                URLSpan(pendingLinkUrl),
                pendingLinkStart,
                builder.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return builder
    }

    private fun appendStyledText(builder: SpannableStringBuilder, text: String, autoLink: Boolean = true) {
        val spanStart = builder.length
        builder.append(text)
        val spanEnd = builder.length

        if (spanStart == spanEnd) return

        if (autoLink) {
            linkifyPlainUrls(builder, text, spanStart)
        }

        var fg = currentFgColor
        var bg = currentBgColor

        if (isReverse) {
            val realBg = if (bg == Color.TRANSPARENT) Color.BLACK else bg
            val tmp = fg
            fg = realBg
            bg = tmp
        }

        if (isDim) {
            fg = Color.argb(
                180,
                Color.red(fg), Color.green(fg), Color.blue(fg)
            )
        }

        builder.setSpan(
            ForegroundColorSpan(fg),
            spanStart, spanEnd,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        if (bg != Color.TRANSPARENT) {
            builder.setSpan(
                BackgroundColorSpan(bg),
                spanStart, spanEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        if (isBold && isItalic) {
            builder.setSpan(
                StyleSpan(Typeface.BOLD_ITALIC),
                spanStart, spanEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        } else if (isBold) {
            builder.setSpan(
                StyleSpan(Typeface.BOLD),
                spanStart, spanEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        } else if (isItalic) {
            builder.setSpan(
                StyleSpan(Typeface.ITALIC),
                spanStart, spanEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        if (isUnderline) {
            builder.setSpan(
                UnderlineSpan(),
                spanStart, spanEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        if (isStrikethrough) {
            builder.setSpan(
                StrikethroughSpan(),
                spanStart, spanEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun linkifyPlainUrls(builder: SpannableStringBuilder, text: String, offset: Int) {
        val matcher = Patterns.WEB_URL.matcher(text)
        while (matcher.find()) {
            var start = matcher.start()
            var end = matcher.end()

            var url = text.substring(start, end)

            if (!url.startsWith("http://", ignoreCase = true) &&
                !url.startsWith("https://", ignoreCase = true)
            ) {
                continue
            }

            while (end > start && url.isNotEmpty() && url.last() in ".,;:!?)]}\"'") {
                end--
                url = text.substring(start, end)
            }
            if (url.isEmpty()) continue

            val fullUrl = url

            builder.setSpan(
                URLSpan(fullUrl),
                offset + start,
                offset + end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.setSpan(
                UnderlineSpan(),
                offset + start,
                offset + end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun parseAnsiParameters(paramsStr: String) {
        if (paramsStr.isEmpty() || paramsStr == "0") {
            resetToDefault()
            return
        }

        val tokens = paramsStr.split(';')
        var i = 0

        while (i < tokens.size) {
            val code = tokens[i].toIntOrNull() ?: 0
            when {
                code == 0 -> resetToDefault()

                code == 1 -> isBold = true
                code == 2 -> isDim = true
                code == 3 -> isItalic = true
                code == 4 -> isUnderline = true
                code == 7 -> isReverse = true
                code == 9 -> isStrikethrough = true

                code == 21 -> isBold = false
                code == 22 -> { isBold = false; isDim = false }
                code == 23 -> isItalic = false
                code == 24 -> isUnderline = false
                code == 27 -> isReverse = false
                code == 29 -> isStrikethrough = false

                code == 39 -> currentFgColor = DEFAULT_FOREGROUND
                code == 49 -> currentBgColor = DEFAULT_BACKGROUND

                code in 30..37 -> currentFgColor = ANSI_16_COLORS[code - 30]
                code in 90..97 -> currentFgColor = ANSI_16_COLORS[code - 90 + 8]

                code in 40..47 -> currentBgColor = ANSI_16_COLORS[code - 40]
                code in 100..107 -> currentBgColor = ANSI_16_COLORS[code - 100 + 8]

                code == 38 -> {
                    if (i + 1 < tokens.size) {
                        val mode = tokens[i + 1].toIntOrNull() ?: 0
                        if (mode == 5 && i + 2 < tokens.size) {
                            val colorId = tokens[i + 2].toIntOrNull() ?: 0
                            currentFgColor = get256Color(colorId)
                            i += 2
                        } else if (mode == 2 && i + 4 < tokens.size) {
                            val r = tokens[i + 2].toIntOrNull() ?: 0
                            val g = tokens[i + 3].toIntOrNull() ?: 0
                            val b = tokens[i + 4].toIntOrNull() ?: 0
                            currentFgColor = Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
                            i += 4
                        }
                    }
                }

                code == 48 -> {
                    if (i + 1 < tokens.size) {
                        val mode = tokens[i + 1].toIntOrNull() ?: 0
                        if (mode == 5 && i + 2 < tokens.size) {
                            val colorId = tokens[i + 2].toIntOrNull() ?: 0
                            currentBgColor = get256Color(colorId)
                            i += 2
                        } else if (mode == 2 && i + 4 < tokens.size) {
                            val r = tokens[i + 2].toIntOrNull() ?: 0
                            val g = tokens[i + 3].toIntOrNull() ?: 0
                            val b = tokens[i + 4].toIntOrNull() ?: 0
                            currentBgColor = Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
                            i += 4
                        }
                    }
                }
            }
            i++
        }
    }

    private fun get256Color(colorId: Int): Int {
        val id = colorId.coerceIn(0, 255)
        return when {
            id < 16 -> ANSI_16_COLORS[id]
            id in 16..231 -> {
                val offset = id - 16
                val r = (offset / 36) * 51
                val g = ((offset % 36) / 6) * 51
                val b = (offset % 6) * 51
                Color.rgb(r, g, b)
            }
            else -> {
                val grayValue = 8 + (id - 232) * 10
                Color.rgb(grayValue, grayValue, grayValue)
            }
        }
    }

    private fun resetToDefault() {
        currentFgColor = DEFAULT_FOREGROUND
        currentBgColor = DEFAULT_BACKGROUND
        isBold = false
        isItalic = false
        isUnderline = false
        isStrikethrough = false
        isReverse = false
        isDim = false
    }

    fun reset() {
        resetToDefault()
    }

    fun stripToPlainText(text: String?): String {
        if (text.isNullOrEmpty()) return text ?: ""
        var result = CSI_PATTERN.matcher(text).replaceAll("")
        result = OSC_PATTERN.matcher(result).replaceAll("")
        result = SIMPLE_ESC_PATTERN.matcher(result).replaceAll("")
        return result
    }
}
