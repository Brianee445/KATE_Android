package com.dti.kate.core

import kotlin.math.pow

/**
 * Small recursive-descent arithmetic evaluator for spoken math ("what's 45
 * times 12", "what is 12 percent of 300", "square root of 81"). Entirely
 * local - no network call, no LLM, so it's exact for what it covers and
 * silently refuses (returns null) for anything it doesn't rather than
 * guessing. Deliberately scoped to arithmetic only, per product decision -
 * no algebra/calculus/unit conversion.
 */
object MathEvaluator {

    /** True if [text] looks like a math question worth trying to evaluate -
     * cheap pre-filter so KateResponseGenerator.classify doesn't run the
     * full parser on every utterance. */
    fun looksLikeMath(lower: String): Boolean {
        val hasDigit = lower.any { it.isDigit() }
        val hasMathWord = MATH_WORDS.any { lower.contains(it) }
        val hasSymbol = lower.any { it in "+-*/^%" }
        return (hasDigit || lower.contains("square root")) && (hasMathWord || hasSymbol)
    }

    /** Returns the formatted result, or null if [text] couldn't be parsed
     * as a supported expression. Never throws. */
    fun evaluate(text: String): String? {
        return try {
            val normalized = normalize(text)
            if (normalized.isBlank()) return null
            val result = Parser(normalized).parseExpression()
            formatResult(result)
        } catch (e: Exception) {
            null
        }
    }

    private val MATH_WORDS = listOf(
        "plus", "minus", "times", "multiplied", "divided", "percent", "%",
        "square root", "squared", "cubed", "power of", "add", "subtract",
    )

    /** Converts spoken/mixed phrasing into a plain symbolic expression the
     * parser understands, and strips filler words around it ("what's",
     * "what is", "calculate", trailing "?"). */
    private fun normalize(raw: String): String {
        var s = raw.lowercase().trim()
        s = s.removeSuffix("?")
        for (filler in listOf(
            "what's", "what is", "whats", "calculate", "compute",
            "can you tell me", "tell me", "how much is",
        )) {
            if (s.startsWith(filler)) s = s.removePrefix(filler).trim()
        }

        // "12 percent of 300" -> "(12/100)*300" handled as a special case
        // before generic word replacement, since "of" here means multiply
        // but "of" elsewhere (there is no elsewhere in this grammar) would not.
        val percentOf = Regex("""([\d.]+)\s*percent\s*of\s*([\d.]+)""")
        percentOf.find(s)?.let { m ->
            val (pct, base) = m.destructured
            return "($pct/100)*$base"
        }

        s = s.replace("square root of", "sqrt")
        s = s.replace(Regex("""([\d.]+)\s*squared"""), "($1^2)")
        s = s.replace(Regex("""([\d.]+)\s*cubed"""), "($1^3)")
        s = s.replace("multiplied by", "*")
        s = s.replace("divided by", "/")
        s = s.replace("times", "*")
        s = s.replace("plus", "+")
        s = s.replace("minus", "-")
        s = s.replace("percent", "/100")
        s = s.replace("x", "*") // "5 x 5" - safe here since normalize runs after word-based ops
        s = s.replace(",", "")
        return s.trim()
    }

    private fun formatResult(value: Double): String {
        // Whole numbers print clean ("12" not "12.0"); everything else
        // rounds to 4 decimal places and trims trailing zeros.
        if (value == value.toLong().toDouble()) return value.toLong().toString()
        val rounded = "%.4f".format(value).trimEnd('0').trimEnd('.')
        return rounded
    }

    /** Minimal recursive-descent parser: expression -> term (+/-) term...,
     * term -> factor (*, /) factor..., factor -> number | sqrt(...) | (expr) | factor^factor. */
    private class Parser(private val input: String) {
        private var pos = 0

        fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                skipSpace()
                when (peek()) {
                    '+' -> { pos++; value += parseTerm() }
                    '-' -> { pos++; value -= parseTerm() }
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parsePower()
            while (true) {
                skipSpace()
                when (peek()) {
                    '*' -> { pos++; value *= parsePower() }
                    '/' -> { pos++; value /= parsePower() }
                    else -> return value
                }
            }
        }

        private fun parsePower(): Double {
            val base = parseFactor()
            skipSpace()
            if (peek() == '^') {
                pos++
                val exponent = parsePower() // right-associative
                return base.pow(exponent)
            }
            return base
        }

        private fun parseFactor(): Double {
            skipSpace()
            if (peek() == '-') { pos++; return -parseFactor() }
            if (peek() == '(') {
                pos++
                val value = parseExpression()
                skipSpace()
                if (peek() == ')') pos++
                return value
            }
            if (input.startsWith("sqrt", pos)) {
                pos += 4
                skipSpace()
                val hasParen = peek() == '('
                if (hasParen) pos++
                val value = parseExpression()
                skipSpace()
                if (hasParen && peek() == ')') pos++
                return kotlin.math.sqrt(value)
            }
            val start = pos
            while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
            if (start == pos) throw IllegalArgumentException("Expected number at $pos in '$input'")
            return input.substring(start, pos).toDouble()
        }

        private fun peek(): Char { skipSpace(); return if (pos < input.length) input[pos] else '\u0000' }
        private fun skipSpace() { while (pos < input.length && input[pos] == ' ') pos++ }
    }
}
