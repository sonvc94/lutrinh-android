package vn.lutrinh.app.domain.catalog

/**
 * A route code (R40, docs/02 "Route code"): 8 characters of [ALPHABET], shown `XXXX-XXXX`; the
 * 8th is the Luhn mod 30 check character of the first 7. Pure Kotlin, mirrored 1:1 in Swift
 * (docs/07); tested against the shared vectors `spec/codes.json` (RouteSpecVectorsTest).
 */
@JvmInline
value class RouteCode private constructor(val value: String) {
    /** `HG4N-7KQE`. */
    val display: String get() = value.substring(0, 4) + "-" + value.substring(4)

    override fun toString(): String = display

    companion object {
        /** 30 characters, value = index: no 0/O, 1/I/L (easy to confuse) and no U. */
        const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ"
        const val LENGTH = 8

        /** Characters a user may type for a missing one: named in the error, never rewritten. */
        private const val CONFUSABLE = "O0I1LU"

        /**
         * Upper-cases ASCII a–z and removes space, tab, CR, LF and `-`; nothing else is rewritten
         * (an `O` stays an `O` and is reported, see [parse]).
         */
        fun normalise(input: String): String = buildString {
            for (ch in input) {
                when (ch) {
                    ' ', '\t', '\r', '\n', '-' -> Unit
                    in 'a'..'z' -> append(ch - 32)
                    else -> append(ch)
                }
            }
        }

        /** The checked code, or why [input] is not one (no network needed). */
        fun parse(input: String): CodeCheck {
            val s = normalise(input)
            if (s.length != LENGTH) return CodeCheck.Invalid(CodeProblem.LENGTH)
            s.firstOrNull { it !in ALPHABET }?.let { bad ->
                return CodeCheck.Invalid(if (bad in CONFUSABLE) CodeProblem.CONFUSABLE else CodeProblem.CHAR)
            }
            if (checkChar(s.substring(0, LENGTH - 1)) != s[LENGTH - 1]) return CodeCheck.Invalid(CodeProblem.CHECK)
            return CodeCheck.Valid(RouteCode(s))
        }

        /** The code or null. */
        fun of(input: String): RouteCode? = (parse(input) as? CodeCheck.Valid)?.code

        /**
         * Luhn mod N over [payload] (all in [ALPHABET]): from the right, every other value starting
         * with the rightmost is doubled and folded as `v / 30 + v % 30`; the check value makes the sum
         * a multiple of 30.
         */
        fun checkChar(payload: String): Char {
            val n = ALPHABET.length
            var sum = 0
            var double = true
            for (i in payload.indices.reversed()) {
                var v = ALPHABET.indexOf(payload[i])
                require(v >= 0) { "not in the alphabet: ${payload[i]}" }
                if (double) {
                    v *= 2
                    v = v / n + v % n
                }
                sum += v
                double = !double
            }
            return ALPHABET[(n - sum % n) % n]
        }
    }
}

/** Result of [RouteCode.parse]. */
sealed interface CodeCheck {
    data class Valid(val code: RouteCode) : CodeCheck
    data class Invalid(val problem: CodeProblem) : CodeCheck
}

/** Why a typed code is refused before anything is sent (spec error + reason). */
enum class CodeProblem(val error: String, val reason: String?) {
    /** Not 8 characters after normalising: the button stays off, no message while typing. */
    LENGTH("bad_format", "length"),

    /** An O, 0, I, 1, L or U: "Mã không có chữ O, I, L, U và số 0, 1". */
    CONFUSABLE("bad_format", "confusable"),

    /** Another character outside the alphabet. */
    CHAR("bad_format", "char"),

    /** The check character does not match: "Có thể gõ nhầm một ký tự". Never sent to the server. */
    CHECK("bad_check", null),
}
