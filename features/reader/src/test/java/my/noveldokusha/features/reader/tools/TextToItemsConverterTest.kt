package my.noveldokusha.features.reader.tools

import kotlinx.coroutines.test.runTest
import my.noveldokusha.features.reader.domain.ReaderItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextToItemsConverterTest {

    private val chapterUrl = "https://example.com/books/test/chapters/1"

    private suspend fun convertToBodies(
        text: String,
        sentenceSplittingEnabled: Boolean = false,
    ): List<ReaderItem.Body> = textToItemsConverter(
        chapterUrl = chapterUrl,
        chapterIndex = 0,
        chapterItemPositionDisplacement = 0,
        text = text,
        sentenceSplittingEnabled = sentenceSplittingEnabled,
    ).filterIsInstance<ReaderItem.Body>()

    private val fourSentenceParagraph = "The first sentence describes the quiet morning in great detail and the empty streets of the town. " +
        "The second one tells about the weather outside and the cold wind that came from the north. " +
        "The third sentence is a bit longer and adds some details about the people and the houses on that street. " +
        "The fourth and final sentence wraps everything up neatly and brings this paragraph to its natural end."

    private val fourExpectedSentences = listOf(
        "The first sentence describes the quiet morning in great detail and the empty streets of the town.",
        "The second one tells about the weather outside and the cold wind that came from the north.",
        "The third sentence is a bit longer and adds some details about the people and the houses on that street.",
        "The fourth and final sentence wraps everything up neatly and brings this paragraph to its natural end.",
    )

    private val singleLongSentenceParagraph = buildString {
        repeat(50) { append("Он шёл по тёмной улице и думал о том что будет дальше, ") }
    }.trim()

    private val longDashDialogue = buildString {
        repeat(9) {
            append("— Привет, как дела? — спросил он, подходя ближе и протягивая руку для рукопожатия. ")
            append("— Хорошо, спасибо, — ответила она и улыбнулась, глядя ему в глаза. ")
        }
    }.trim()

    private val litrpgStatusBlock = "Имя: Джон\nУровень: 5\nHP: 120/120\nMP: 100/100\n" +
        "Сила: 15\nЛовкость: 12\nИнтеллект: 18\nМудрость: 14\nХаризма: 11\nВыносливость: 20\n" +
        "Скорость: 16\nУдача: 8\nЗдоровье: 120\nМана: 90\nОпыт: 3500\nЗолото: 450\n" +
        "Атака: 30\nЗащита: 25\nСопротивление огню: 5\nСопротивление холоду: 5\nСопротивление яду: 10\n" +
        "Регенерация: 2\nСкрытность: 17\nВосприятие: 9\nЛидерство: 13\nТорговля: 6\n" +
        "Красноречие: 14\nКузнечное дело: 11\nАлхимия: 12\nРемесло: 10\nЗемледелие: 7"

    @Test
    fun featureOff_default_longSingleSentenceParagraph_splitByGuard() = runTest {
        assertTrue(singleLongSentenceParagraph.length > 2000)
        val bodies = convertToBodies(singleLongSentenceParagraph)
        assertTrue("guard should split a >2000-char paragraph into multiple bodies", bodies.size > 1)
    }

    @Test
    fun featureOff_default_ordinaryParagraph_keptAsOneBody() = runTest {
        val bodies = convertToBodies(fourSentenceParagraph)
        assertEquals(listOf(fourSentenceParagraph), bodies.map { it.text })
    }

    @Test
    fun featureOn_fourSentenceParagraph_splitsIntoFourBodiesInOrder() = runTest {
        val bodies = convertToBodies(fourSentenceParagraph, sentenceSplittingEnabled = true)
        assertEquals(fourExpectedSentences, bodies.map { it.text })
    }

    @Test
    fun featureOn_shortParagraphUnder250_keptAsOneBody() = runTest {
        val text = "He walked home. The sun was setting."
        val bodies = convertToBodies(text, sentenceSplittingEnabled = true)
        assertEquals(listOf(text), bodies.map { it.text })
    }

    @Test
    fun featureOn_longDashDialogue_keptAsOneBody() = runTest {
        assertTrue(longDashDialogue.length > 800)
        val bodies = convertToBodies(longDashDialogue, sentenceSplittingEnabled = true)
        assertEquals(listOf(longDashDialogue), bodies.map { it.text })
    }

    @Test
    fun featureOn_longParagraphWithoutSentenceBoundary_keptAsOneBody() = runTest {
        assertTrue(singleLongSentenceParagraph.length > 800)
        val bodies = convertToBodies(singleLongSentenceParagraph, sentenceSplittingEnabled = true)
        assertEquals(listOf(singleLongSentenceParagraph), bodies.map { it.text })
    }

    @Test
    fun featureOn_litrpgStatusBlock_keptWholeAsOneBody() = runTest {
        val text = litrpgStatusBlock + "\n\n" + "The story continues after the status window closes."
        val bodies = convertToBodies(text, sentenceSplittingEnabled = true)
        assertEquals(2, bodies.size)
        assertEquals(litrpgStatusBlock, bodies[0].text)
        assertEquals("The story continues after the status window closes.", bodies[1].text)
    }

    @Test
    fun featureOn_blankLineSeparatedParagraphs_staySeparateBodies() = runTest {
        val text = "He walked home through the quiet streets.\n\nShe waited by the door for his return.\n\nThey sat together and talked about the day."
        val bodies = convertToBodies(text, sentenceSplittingEnabled = true)
        assertEquals(
            listOf(
                "He walked home through the quiet streets.",
                "She waited by the door for his return.",
                "They sat together and talked about the day.",
            ),
            bodies.map { it.text }
        )
    }
}