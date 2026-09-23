package my.noveldokusha.features.reader.tools

/**
 * Rule-based paragraph-to-sentences splitter.
 *
 * Pure Kotlin stdlib, no dependencies. A paragraph is only split when it is long
 * enough and contains at least one valid sentence boundary; dialogue, status
 * blocks (litRPG) and dash-dialogue paragraphs are always kept whole.
 */
object SentenceSplitter {

    private const val MIN_PARAGRAPH_LENGTH = 250
    private const val SHORT_STATUS_LINE_LENGTH = 40
    private const val EM_DASH = '\u2014'
    private const val EN_DASH = '\u2013'

    /** Terminators that require boundary validation: . ! ? */
    private val BASIC_TERMINATORS = setOf('.', '!', '?')

    /** Script terminators: the boundary is always valid (never inside quotes/brackets). */
    private val SCRIPT_TERMINATORS = setOf(
        // CJK / Japanese / Chinese / Korean
        '\u3002', // 。
        '\uFF01', // ！
        '\uFF1F', // ？
        '\uFF0E', // ．
        // Arabic / Urdu
        '\u061F', // ؟
        '\u06D4', // ۔
        // Devanagari
        '\u0964', // ।
        '\u0965', // ॥
        // Armenian
        '\u0589', // ։
        // Burmese
        '\u104A', // ။
        '\u104B', // ၌
        // Ethiopic
        '\u1362', // ።
        '\u1367', // ፧
        '\u1368'  // ፨
    )

    private val ALL_TERMINATORS = BASIC_TERMINATORS + SCRIPT_TERMINATORS

    /** Quote pairs: closing char -> its opening char. */
    private val CLOSING_TO_OPENING_QUOTES = mapOf(
        '"' to '"',                             // "…"
        '»' to '«',                             // «…»
        '\u201C' to '\u201E',                   // "…" closes „…
        '\u201D' to '\u201C',                   // ”…" closes “…
        '\u2019' to '\u2018',                   // ’…' closes ‘…
        '\u300D' to '\u300C',                   // 」…「
        '\u300F' to '\u300E',                   // 』…『
        '\u300B' to '\u300A',                   // 》…《
        '\u203A' to '\u2039'                    // ›…‹
    )

    /** Opening quotes. A char may be both an opening and a closing quote (" and “). */
    private val OPENING_QUOTES = setOf(
        '"',
        '«',
        '\u201E', // „
        '\u201C', // “
        '\u2018', // ‘
        '\u300C', // 「
        '\u300E', // 『
        '\u300A', // 《
        '\u2039'  // ‹
    )

    private val CLOSING_TO_OPENING_BRACKETS = mapOf(
        ')' to '(', ']' to '[', '}' to '{',
        '\u27E9' to '\u27E8', // ⟩…⟨
        '\u3015' to '\u3014', // 〕…〔
        '\u3011' to '\u3010'  // 】…【
    )

    private val OPENING_BRACKETS = setOf('(', '[', '{', '\u27E8', '\u3014', '\u3010')

    /** Known abbreviations (lowercase, without the trailing dot). */
    private val ABBREVIATIONS = setOf(
        "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "no", "vs", "etc", "fig", "vol",
        "pp", "ed", "inc", "ltd", "co", "corp", "approx", "capt", "cf", "col", "gen", "mag",
        "med", "mt", "nat", "nr", "phil", "rer", "sci", "sgt", "univ", "zb", "dh", "ua", "usw",
        "bspw", "ca", "bzw", "т", "г", "гг", "гл", "стр", "см", "рис", "табл", "ул", "руб",
        "проф", "др", "до", "н", "э", "янв", "фев", "мар", "апр", "авг", "сен", "окт", "ноя", "дек",
        "д", "ч", "с", "п", "тыс", "млн", "им", "км", "z"
    )

    /** litRPG status line "key: value". */
    private val STATUS_KEY_VALUE_LINE = Regex("^\\s*[«\"“\"\\[(【]*[\\p{L}\\p{N}_ \\-]{1,30}[:：]\\s*\\S.*")

    /** litRPG section divider line: ***, ===, ---, ──, ━━, ══, ╔, ╚, ║, ┌, └. */
    private val STATUS_DIVIDER_LINE = Regex("^\\s*(?:\\*{3,}|={3,}|-{3,}|─{2,}|━{2,}|═{2,}|╔|╚|║|┌|└)\\s*$")

    /** Punctuation that continues a sentence rather than ending it ("и т. п., потому…"). */
    private val SENTENCE_CONTINUATION = setOf(',', ';', ':')

    /**
     * Splits a paragraph into sentences.
     *
     * Returns [paragraph] unchanged when it is too short to split, is a litRPG
     * status block or dash dialogue, or contains no valid sentence boundary.
     */
    fun splitParagraph(paragraph: String): List<String> {
        if (paragraph.length < MIN_PARAGRAPH_LENGTH) return listOf(paragraph)
        if (isStatusBlock(paragraph) || isDashDialogue(paragraph)) return listOf(paragraph)
        val boundaries = findValidBoundaries(paragraph)
        if (boundaries.isEmpty()) return listOf(paragraph)

        val segments = mutableListOf<String>()
        var start = 0
        for (boundary in boundaries) {
            segments.add(paragraph.substring(start, boundary + 1).trim())
            start = boundary + 1
        }
        segments.add(paragraph.substring(start).trim())
        return segments.filter { it.isNotEmpty() }
    }

    /**
     * True when the paragraph is long enough and contains at least one valid
     * sentence boundary (and is not a status block or dash dialogue).
     */
    fun needsSplitting(paragraph: String): Boolean {
        if (paragraph.length < MIN_PARAGRAPH_LENGTH) return false
        if (isStatusBlock(paragraph) || isDashDialogue(paragraph)) return false
        return findValidBoundaries(paragraph).isNotEmpty()
    }

    /**
     * Scans the paragraph left to right with a quote/bracket stack. A terminator
     * is a split candidate only at stack depth 0; script terminators always yield
     * a boundary, basic ones ( . ! ? ) are validated.
     */
    private fun findValidBoundaries(paragraph: String): List<Int> {
        val stack = ArrayDeque<Char>()
        val boundaries = mutableListOf<Int>()

        var i = 0
        while (i < paragraph.length) {
            val ch = paragraph[i]
            when {
                CLOSING_TO_OPENING_QUOTES.containsKey(ch) -> {
                    val opener = CLOSING_TO_OPENING_QUOTES.getValue(ch)
                    if (stack.isNotEmpty() && stack.last() == opener) {
                        stack.removeLast()
                        // A terminator directly before a closing quote-pair is
                        // attached to it: the boundary is placed right after the
                        // closing quote ("大丈夫だ。」 そして去った。" → 2 segments).
                        val prev = paragraph.getOrNull(i - 1)
                        if (stack.isEmpty() && prev != null && prev in ALL_TERMINATORS &&
                            isBoundaryValid(paragraph, prev, i - 1, i + 1)
                        ) {
                            boundaries.add(i)
                        }
                    } else if (ch in OPENING_QUOTES) {
                        stack.addLast(ch)
                    }
                }
                ch in OPENING_QUOTES -> stack.addLast(ch)
                CLOSING_TO_OPENING_BRACKETS.containsKey(ch) -> {
                    val opener = CLOSING_TO_OPENING_BRACKETS.getValue(ch)
                    if (stack.isNotEmpty() && stack.last() == opener) stack.removeLast()
                }
                ch in OPENING_BRACKETS -> stack.addLast(ch)
                ch in ALL_TERMINATORS -> {
                    // Adjacent punctuation runs ("?!", "!?", "....") belong together: the
                    // boundary is placed on the last terminator only, otherwise empty
                    // "!" / "." / "?" segments are produced.
                    val rightAfter = paragraph.getOrNull(i + 1)
                    if (rightAfter == null || rightAfter !in ALL_TERMINATORS) {
                        if (stack.isEmpty() && isBoundaryValid(paragraph, ch, i, i + 1)) boundaries.add(i)
                    }
                }
            }
            i++
        }
        return boundaries
    }

    /**
     * Validates a terminator at [terminatorPos] (basic . ! ? only; script
     * terminators are always valid). [nextFrom] is where the "next significant
     * char" scan starts — right after the terminator, or after a closing quote
     * the terminator was attached to.
     */
    private fun isBoundaryValid(paragraph: String, terminator: Char, terminatorPos: Int, nextFrom: Int): Boolean {
        // Script-specific terminators (。！？．؟۔।॥։။၌።፧፨) always yield a boundary.
        if (terminator !in BASIC_TERMINATORS) return true

        val next = nextSignificantChar(paragraph, nextFrom)
        val sigAfter = nextSignificantChar(paragraph, terminatorPos + 1)
        val after = paragraph.getOrNull(terminatorPos + 1)
        val before = paragraph.getOrNull(terminatorPos - 1)

        // a) A letter immediately after (no space): "т.д.", "z.B.", "И.В.".
        if (after != null && isLatinCyrillicLetter(after)) return false

        // b) A digit as the next significant char (spaces allowed): "No. 1", "3.14", "ч. 2".
        if (sigAfter != null && sigAfter.isDigit()) return false
        if (before != null && before.isDigit() && after != null && after.isDigit()) return false

        // c) The last Latin/Cyrillic word before the terminator is an abbreviation: "Dr.", "St.", "г.".
        // A single-letter member of a dotted chain ("т.д." -> "д") does not count — the chain's
        // final dot can still end a sentence ("…и т.д. И ушёл…").
        val lastWord = lastLatinWordBefore(paragraph, terminatorPos)
        if (lastWord != null && lastWord in ABBREVIATIONS &&
            paragraph.getOrNull(terminatorPos - lastWord.length - 1) != '.'
        ) return false

        // d) A single uppercase initial followed by an uppercase word: "И. Иванов".
        if (before != null && isLatinCyrillicLetter(before) && before.isUpperCase() &&
            isSingleLetterWord(paragraph, terminatorPos - 1) &&
            next != null && next.isLetter() && next.isUpperCase()
        ) return false

        // e) The next significant word starts with a lowercase letter: "Что? спросил он".
        if (next != null && next.isLetter() && next.isLowerCase()) return false

        // f) A comma, semicolon or colon right after continues the sentence: "и т. п., потому…".
        if (sigAfter != null && sigAfter in SENTENCE_CONTINUATION) return false

        // g) A dash right after is a dialogue attribution: "«Беги!» — крикнул он".
        if (next != null && (next == EM_DASH || next == EN_DASH)) return false

        return true
    }

    private fun nextSignificantChar(paragraph: String, from: Int): Char? {
        var i = from
        while (i < paragraph.length && paragraph[i].isWhitespace()) i++
        return if (i < paragraph.length) paragraph[i] else null
    }

    /** The last contiguous run of Latin/Cyrillic letters ending right before the terminator. */
    private fun lastLatinWordBefore(paragraph: String, terminatorPos: Int): String? {
        val end = terminatorPos - 1
        if (end < 0 || !isLatinCyrillicLetter(paragraph[end])) return null
        var start = end
        while (start >= 0 && isLatinCyrillicLetter(paragraph[start])) start--
        return paragraph.substring(start + 1, end + 1).lowercase()
    }

    /** True when the letter at [letterPos] forms a single-letter word. */
    private fun isSingleLetterWord(paragraph: String, letterPos: Int): Boolean {
        val before = paragraph.getOrNull(letterPos - 1)
        return before == null || !isLatinCyrillicLetter(before)
    }

    /** Latin (incl. extended) or Cyrillic letter: U+0041..U+024F or U+0400..U+052F. */
    private fun isLatinCyrillicLetter(c: Char): Boolean {
        if (!c.isLetter()) return false
        val code = c.code
        return code in 0x0041..0x024F || code in 0x0400..0x052F
    }

    /**
     * True when the paragraph looks like a litRPG status block and must be kept
     * whole: >= 2 "key: value" lines, or any divider line, or >= 2 short lines
     * without any terminator.
     */
    private fun isStatusBlock(paragraph: String): Boolean {
        var keyValueLines = 0
        var shortNoTerminatorLines = 0
        for (line in paragraph.split('\n')) {
            if (STATUS_KEY_VALUE_LINE.matches(line)) keyValueLines++
            if (STATUS_DIVIDER_LINE.matches(line)) return true
            if (line.length < SHORT_STATUS_LINE_LENGTH && line.none { it in ALL_TERMINATORS }) {
                shortNoTerminatorLines++
            }
        }
        return keyValueLines >= 2 || shortNoTerminatorLines >= 2
    }

    private fun isDashDialogue(paragraph: String): Boolean {
        val trimmed = paragraph.trimStart()
        return trimmed.startsWith(EM_DASH) || trimmed.startsWith(EN_DASH)
    }
}