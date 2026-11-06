package com.tool.tree

object ChatMath {
    private val SYMS = mapOf(
        "alpha" to "α",
        "beta" to "β",
        "gamma" to "γ",
        "delta" to "δ",
        "epsilon" to "ε",
        "varepsilon" to "ε",
        "zeta" to "ζ",
        "eta" to "η",
        "theta" to "θ",
        "vartheta" to "ϑ",
        "iota" to "ι",
        "kappa" to "κ",
        "lambda" to "λ",
        "mu" to "μ",
        "nu" to "ν",
        "xi" to "ξ",
        "pi" to "π",
        "varpi" to "ϖ",
        "rho" to "ρ",
        "sigma" to "σ",
        "varsigma" to "ς",
        "tau" to "τ",
        "upsilon" to "υ",
        "phi" to "φ",
        "varphi" to "φ",
        "chi" to "χ",
        "psi" to "ψ",
        "omega" to "ω",
        "Gamma" to "Γ",
        "Delta" to "Δ",
        "Theta" to "Θ",
        "Lambda" to "Λ",
        "Xi" to "Ξ",
        "Pi" to "Π",
        "Sigma" to "Σ",
        "Upsilon" to "Υ",
        "Phi" to "Φ",
        "Psi" to "Ψ",
        "Omega" to "Ω",
        "times" to "×",
        "cdot" to "·",
        "div" to "÷",
        "pm" to "±",
        "mp" to "∓",
        "leq" to "≤",
        "le" to "≤",
        "geq" to "≥",
        "ge" to "≥",
        "neq" to "≠",
        "ne" to "≠",
        "approx" to "≈",
        "equiv" to "≡",
        "sim" to "∼",
        "propto" to "∝",
        "infty" to "∞",
        "to" to "→",
        "rightarrow" to "→",
        "leftarrow" to "←",
        "gets" to "←",
        "Rightarrow" to "⇒",
        "Leftarrow" to "⇐",
        "leftrightarrow" to "↔",
        "Leftrightarrow" to "⇔",
        "iff" to "⇔",
        "implies" to "⟹",
        "mapsto" to "↦",
        "sum" to "∑",
        "prod" to "∏",
        "int" to "∫",
        "oint" to "∮",
        "partial" to "∂",
        "nabla" to "∇",
        "in" to "∈",
        "notin" to "∉",
        "ni" to "∋",
        "subset" to "⊂",
        "subseteq" to "⊆",
        "supset" to "⊃",
        "supseteq" to "⊇",
        "cup" to "∪",
        "cap" to "∩",
        "emptyset" to "∅",
        "varnothing" to "∅",
        "forall" to "∀",
        "exists" to "∃",
        "neg" to "¬",
        "lnot" to "¬",
        "land" to "∧",
        "wedge" to "∧",
        "lor" to "∨",
        "vee" to "∨",
        "ldots" to "…",
        "dots" to "…",
        "cdots" to "⋯",
        "circ" to "∘",
        "angle" to "∠",
        "degree" to "°",
        "perp" to "⊥",
        "parallel" to "∥",
        "ll" to "≪",
        "gg" to "≫",
        "star" to "⋆",
        "ast" to "∗",
        "bullet" to "•",
        "oplus" to "⊕",
        "otimes" to "⊗",
        "hbar" to "ℏ",
        "ell" to "ℓ",
        "Re" to "ℜ",
        "Im" to "ℑ",
        "aleph" to "ℵ",
        "prime" to "′",
        "lim" to "lim",
        "log" to "log",
        "ln" to "ln",
        "sin" to "sin",
        "cos" to "cos",
        "tan" to "tan",
        "cot" to "cot",
        "sec" to "sec",
        "csc" to "csc",
        "arcsin" to "arcsin",
        "arccos" to "arccos",
        "arctan" to "arctan",
        "sinh" to "sinh",
        "cosh" to "cosh",
        "tanh" to "tanh",
        "exp" to "exp",
        "max" to "max",
        "min" to "min",
        "sup" to "sup",
        "inf" to "inf",
        "det" to "det",
        "gcd" to "gcd",
        "mod" to "mod",
        "bmod" to "mod",
        "Pr" to "Pr",
        "deg" to "deg",
        "dim" to "dim",
        "ker" to "ker",
        "arg" to "arg",
        "quad" to " ",
        "qquad" to "  ",
        "space" to " "
    )

    private val COMB = mapOf(
        "bar" to "̄",
        "overline" to "̄",
        "hat" to "̂",
        "widehat" to "̂",
        "tilde" to "̃",
        "widetilde" to "̃",
        "vec" to "⃗",
        "dot" to "̇",
        "ddot" to "̈"
    )

    private val WRAP = setOf("bm", "boldsymbol", "displaystyle", "mathbb", "mathbf", "mathcal", "mathfrak", "mathit", "mathrm", "mathsf", "mathtt", "mbox", "operatorname", "scriptstyle", "text", "textbf", "textit", "textrm", "textstyle")
    private val NO_OUTPUT = setOf("displaystyle", "textstyle", "scriptstyle")
    private val DELIMS = setOf("left", "right", "big", "Big", "bigg", "Bigg")
    private val SKIP = setOf("limits", "nolimits")

    private val SUPM = mapOf(
        '0' to '⁰',
        '1' to '¹',
        '2' to '²',
        '3' to '³',
        '4' to '⁴',
        '5' to '⁵',
        '6' to '⁶',
        '7' to '⁷',
        '8' to '⁸',
        '9' to '⁹',
        '+' to '⁺',
        '-' to '⁻',
        '=' to '⁼',
        '(' to '⁽',
        ')' to '⁾',
        'n' to 'ⁿ',
        'i' to 'ⁱ'
    )

    private val SUBM = mapOf(
        '0' to '₀',
        '1' to '₁',
        '2' to '₂',
        '3' to '₃',
        '4' to '₄',
        '5' to '₅',
        '6' to '₆',
        '7' to '₇',
        '8' to '₈',
        '9' to '₉',
        '+' to '₊',
        '-' to '₋',
        '=' to '₌',
        '(' to '₍',
        ')' to '₎',
        'a' to 'ₐ',
        'e' to 'ₑ',
        'o' to 'ₒ',
        'x' to 'ₓ',
        'h' to 'ₕ',
        'k' to 'ₖ',
        'l' to 'ₗ',
        'm' to 'ₘ',
        'n' to 'ₙ',
        'p' to 'ₚ',
        's' to 'ₛ',
        't' to 'ₜ'
    )

    private val SIMPLE = Regex("[\\w.\\u0370-\\u03ff\u00B2\u00B3]+")
    private val MULTI_SPACE = Regex("[ \\t]{2,}")
    private val MATH_HINT = Regex("[\\\\^_{}=+\\-*/<>]")
    private const val MD_SPECIAL = "\\`*_{}[]~<=^!"

    private class Group(val text: String, val next: Int)

    private fun readGroup(s: String, from: Int): Group {
        val n = s.length
        var i = from
        while (i < n && s[i] == ' ') i++
        if (i >= n) return Group("", n)
        if (s[i] == '{') {
            var depth = 0
            var j = i
            while (j < n) {
                if (s[j] == '\\' && j + 1 < n) {
                    j += 2
                    continue
                }
                if (s[j] == '{') depth++
                else if (s[j] == '}') {
                    depth--
                    if (depth == 0) return Group(s.substring(i + 1, j), j + 1)
                }
                j++
            }
            return Group(s.substring(i + 1), n)
        }
        if (s[i] == '\\') {
            var j = i + 1
            if (j < n && s[j].isLetter()) {
                while (j < n && s[j].isLetter()) j++
            } else {
                j = minOf(n, j + 1)
            }
            return Group(s.substring(i, j), j)
        }
        return Group(s[i].toString(), i + 1)
    }

    private fun isSimple(t: String) = t.isNotEmpty() && SIMPLE.matches(t)

    private fun wrapArg(t: String) = if (isSimple(t)) t else "($t)"

    private fun script(arg: String, map: Map<Char, Char>, mark: String): String {
        if (arg.isNotEmpty() && arg.all { map.containsKey(it) }) {
            val sb = StringBuilder()
            for (ch in arg) sb.append(map[ch])
            return sb.toString()
        }
        return if (arg.length == 1) mark + arg else "$mark($arg)"
    }

    private fun escapedChar(nx: Char): String = when (nx) {
        ',', ';', ':', '>', '\\', ' ' -> " "
        '!' -> ""
        '|' -> "\u2016"
        else -> nx.toString()
    }

    fun toUnicode(tex: String): String {
        val out = StringBuilder()
        convInto(tex, out)
        return MULTI_SPACE.replace(out.toString(), " ").trim()
    }

    private fun conv(s: String): String {
        val sb = StringBuilder()
        convInto(s, sb)
        return sb.toString()
    }

    private fun convInto(s: String, out: StringBuilder) {
        var i = 0
        val n = s.length
        while (i < n) {
            val c = s[i]
            if (c == '\\') {
                if (i + 1 >= n) break
                if (s[i + 1].isLetter()) {
                    var j = i + 1
                    while (j < n && s[j].isLetter()) j++
                    val name = s.substring(i + 1, j)
                    when {
                        name == "frac" || name == "dfrac" || name == "tfrac" -> {
                            val a = readGroup(s, j)
                            val b = readGroup(s, a.next)
                            out.append(wrapArg(conv(a.text))).append('/').append(wrapArg(conv(b.text)))
                            i = b.next
                        }
                        name == "binom" -> {
                            val a = readGroup(s, j)
                            val b = readGroup(s, a.next)
                            out.append("C(").append(conv(a.text)).append(", ").append(conv(b.text)).append(')')
                            i = b.next
                        }
                        name == "sqrt" -> {
                            var k = j
                            var idx = ""
                            while (k < n && s[k] == ' ') k++
                            if (k < n && s[k] == '[') {
                                val e = s.indexOf(']', k)
                                if (e > 0) {
                                    idx = conv(s.substring(k + 1, e))
                                    k = e + 1
                                }
                            }
                            val g = readGroup(s, k)
                            if (idx.isNotEmpty()) {
                                if (idx.all { SUPM.containsKey(it) }) {
                                    for (ch in idx) out.append(SUPM[ch])
                                } else {
                                    out.append(idx)
                                }
                            }
                            out.append('\u221A').append(wrapArg(conv(g.text)))
                            i = g.next
                        }
                        name in WRAP -> {
                            if (name in NO_OUTPUT) {
                                i = j
                            } else {
                                val g = readGroup(s, j)
                                out.append(conv(g.text))
                                i = g.next
                            }
                        }
                        name in DELIMS || name in SKIP -> {
                            var k = j
                            if (name in DELIMS && k < n && s[k] == '.') k++
                            i = k
                        }
                        COMB.containsKey(name) -> {
                            val g = readGroup(s, j)
                            val t = conv(g.text)
                            out.append(t)
                            if (t.length == 1) out.append(COMB[name])
                            i = g.next
                        }
                        else -> {
                            out.append(SYMS[name] ?: name)
                            i = j
                        }
                    }
                    continue
                }
                out.append(escapedChar(s[i + 1]))
                i += 2
                continue
            }
            if (c == '^' || c == '_') {
                val g = readGroup(s, i + 1)
                val t = conv(g.text)
                out.append(if (c == '^') script(t, SUPM, "^") else script(t, SUBM, "_"))
                i = g.next
                continue
            }
            if (c == '{' || c == '}') {
                i++
                continue
            }
            if (c == '~' || c == '&') {
                out.append(' ')
                i++
                continue
            }
            out.append(c)
            i++
        }
    }

    private fun escapeMd(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            if (MD_SPECIAL.indexOf(ch) >= 0) sb.append('\\')
            sb.append(ch)
        }
        return sb.toString()
    }

    private fun findInlineEnd(s: String, from: Int): Int {
        val n = s.length
        var j = from
        while (j < n && j - from <= 300) {
            val ch = s[j]
            if (ch == '\n' || ch == '`') return -1
            if (ch == '\\') {
                j += 2
                continue
            }
            if (ch == '$') {
                val closes = j > from && !s[j - 1].isWhitespace() && (j + 1 >= n || !s[j + 1].isDigit())
                return if (closes) j else -1
            }
            j++
        }
        return -1
    }

    private fun appendMath(out: StringBuilder, tex: String, display: Boolean, s: String, after: Int) {
        val text = escapeMd(toUnicode(tex.replace('\n', ' ')))
        if (display) {
            if (out.isNotEmpty() && out[out.length - 1] != '\n') out.append('\n')
            out.append(text)
            if (after < s.length && s[after] != '\n') out.append('\n')
        } else {
            out.append(text)
        }
    }

    fun convertMath(s: String): String {
        if (s.indexOf('$') < 0 && !s.contains("\\(") && !s.contains("\\[")) return s
        val out = StringBuilder()
        var i = 0
        val n = s.length
        while (i < n) {
            val c = s[i]
            if (c == '\\' && i + 1 < n) {
                val d = s[i + 1]
                if (d == '(' || d == '[') {
                    val end = s.indexOf(if (d == '(') "\\)" else "\\]", i + 2)
                    if (end > 0) {
                        appendMath(out, s.substring(i + 2, end), d == '[', s, end + 2)
                        i = end + 2
                        continue
                    }
                }
                out.append(c).append(d)
                i += 2
                continue
            }
            if (c == '$' && i + 1 < n) {
                if (s[i + 1] == '$') {
                    val end = s.indexOf("\$\$", i + 2)
                    if (end > 0) {
                        appendMath(out, s.substring(i + 2, end), true, s, end + 2)
                        i = end + 2
                        continue
                    }
                } else if (!s[i + 1].isWhitespace()) {
                    val end = findInlineEnd(s, i + 1)
                    if (end > 0 && MATH_HINT.containsMatchIn(s.substring(i + 1, end))) {
                        appendMath(out, s.substring(i + 1, end), false, s, end + 1)
                        i = end + 1
                        continue
                    }
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }
}
