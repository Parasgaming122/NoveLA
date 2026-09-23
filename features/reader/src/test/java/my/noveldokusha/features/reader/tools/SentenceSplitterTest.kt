package my.noveldokusha.features.reader.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSplitterTest {

    @Test
    fun english_longParagraph_splitsIntoSentences() {
        val input = "He walked through the quiet streets of the old town, watching the lights flicker on in the windows of the houses he passed one by one. The sun was setting slowly behind the distant hills, painting the whole sky in warm shades of orange and pink. Birds sang their evening songs from the treetops, filling the quiet air with gentle and soothing melodies."
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "He walked through the quiet streets of the old town, watching the lights flicker on in the windows of the houses he passed one by one.",
                "The sun was setting slowly behind the distant hills, painting the whole sky in warm shades of orange and pink.",
                "Birds sang their evening songs from the treetops, filling the quiet air with gentle and soothing melodies."
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }

    @Test
    fun shortParagraph_twoSentences_notSplit() {
        val input = "He walked home. The sun was setting."
        assertEquals(listOf(input), SentenceSplitter.splitParagraph(input))
    }

    @Test
    fun longParagraph_twoSentences_splitsIntoTwo() {
        val input = "He walked through the quiet streets of the old town, watching the lights flicker on in the windows of the houses he passed one by one in the cold evening air. The sun was setting slowly behind the distant hills, painting the sky in warm shades of orange, pink and deep purple."
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "He walked through the quiet streets of the old town, watching the lights flicker on in the windows of the houses he passed one by one in the cold evening air.",
                "The sun was setting slowly behind the distant hills, painting the sky in warm shades of orange, pink and deep purple."
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }

    @Test
    fun veryLongSingleSentence_notSplit() {
        val input = "He stood there in silence, thinking about everything that had happened and wondering what to do next" +
            ", unable to move, unable to speak, unable to make a single decision about the path that lay ahead of him" +
            ", while the last rays of the evening sun disappeared behind the rooftops of the old district" +
            ", and the first stars began to appear in the darkening sky above his head" +
            ", and the cold wind picked up, rustling the dry leaves on the pavement" +
            ", and the streetlights flickered on one after another, casting long shadows on the ground" +
            ", and somewhere far away a dog barked and a window slammed shut" +
            ", and he finally took a deep breath and stepped forward into the unknown" +
            ", not knowing what the morning would bring or whether he would ever see his old friends again" +
            ", because everything he had believed in for so many years had suddenly lost its meaning" +
            ", and the only thing left to do was to keep walking and hope for better days"
        assertTrue(input.length > 800)
        assertEquals(listOf(input), SentenceSplitter.splitParagraph(input))
    }

    @Test
    fun quotedDialogue_short_paragraphKeptWhole() {
        val input = "\"Hello,\" she said. \"Goodbye.\""
        assertEquals(listOf(input), SentenceSplitter.splitParagraph(input))
    }

    @Test
    fun russianGuillemets_innerSentences_keptWhole() {
        val input = "Он сказал: «Привет. Как дела?» и ушёл, не дожидаясь ответа, а она осталась стоять посреди комнаты и смотреть ему вслед, пока дверь за ним не закрылась и пока шаги в коридоре не стихли совсем, и тогда она медленно опустилась на стул и обвела взглядом опустевшую комнату, где ещё минуту назад было так тепло и уютно."
        assertTrue(input.length > 250)
        assertEquals(listOf(input), SentenceSplitter.splitParagraph(input))
    }

    @Test
    fun parentheses_innerPeriods_keptInside_splitOutside() {
        val input = "Let me explain the situation in full detail (see the attached note on the previous page. It matters a great deal for what follows in this chapter). The next sentence starts right here and continues with a lot of additional words that describe the circumstances of the evening in great detail."
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "Let me explain the situation in full detail (see the attached note on the previous page. It matters a great deal for what follows in this chapter).",
                "The next sentence starts right here and continues with a lot of additional words that describe the circumstances of the evening in great detail."
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }

    @Test
    fun abbreviation_dr_doesNotSplit() {
        val input = "Dr. Smith went home after a long day at the clinic where he had treated many patients, written detailed reports and filled out all the paperwork for the week. He was tired but satisfied with the work he had done that day, and he was already looking forward to a quiet evening at home with a good book."
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "Dr. Smith went home after a long day at the clinic where he had treated many patients, written detailed reports and filled out all the paperwork for the week.",
                "He was tired but satisfied with the work he had done that day, and he was already looking forward to a quiet evening at home with a good book."
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }

    @Test
    fun abbreviation_no_doesNotSplit() {
        val input = "No. 1 is the best option for anyone who wants to learn quickly and efficiently, so we always recommend it to beginners who start from scratch. Try it yourself and you will see the difference immediately, without spending too much time on complicated practice."
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "No. 1 is the best option for anyone who wants to learn quickly and efficiently, so we always recommend it to beginners who start from scratch.",
                "Try it yourself and you will see the difference immediately, without spending too much time on complicated practice."
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }

    @Test
    fun abbreviation_pm_doesNotSplit() {
        val input = "The meeting is at 3 p.m. as usual, so we need to prepare the reports and the presentation slides well in advance of tomorrow morning, so we can connect without any delays. We left the office a bit earlier to have enough time for all the preparations and for a quick dinner before the call."
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "The meeting is at 3 p.m. as usual, so we need to prepare the reports and the presentation slides well in advance of tomorrow morning, so we can connect without any delays.",
                "We left the office a bit earlier to have enough time for all the preparations and for a quick dinner before the call."
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }

    @Test
    fun abbreviation_russian_g_doesNotSplit() {
        val input = "Он пришёл в 2024 г. и сразу принялся за работу над новым проектом, который требовал его постоянного внимания и полной сосредоточенности с самого утра до позднего вечера. И ушёл он только поздно вечером, когда все дела были наконец закончены и можно было спокойно выдохнуть и отдохнуть."
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "Он пришёл в 2024 г. и сразу принялся за работу над новым проектом, который требовал его постоянного внимания и полной сосредоточенности с самого утра до позднего вечера.",
                "И ушёл он только поздно вечером, когда все дела были наконец закончены и можно было спокойно выдохнуть и отдохнуть."
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }

    @Test
    fun initials_chain_doesNotSplit() {
        val input = "И.В. Иванов пришёл на встречу раньше всех и занял место у окна в дальнем конце кабинета, рядом с большим шкафом с документами, и стал ждать начала обсуждения. Он сел и принялся перечитывать свои заметки перед началом обсуждения, чтобы освежить в памяти все ключевые цифры и факты, о которых собирался рассказать."
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "И.В. Иванов пришёл на встречу раньше всех и занял место у окна в дальнем конце кабинета, рядом с большим шкафом с документами, и стал ждать начала обсуждения.",
                "Он сел и принялся перечитывать свои заметки перед началом обсуждения, чтобы освежить в памяти все ключевые цифры и факты, о которых собирался рассказать."
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }

    @Test
    fun initials_singleLetterName_doesNotSplit() {
        val input = "Я сегодня случайно встретил И. Иванова на вокзале, и мы немного поговорили о погоде, о работе и о наших общих знакомых, которых не видели уже много лет. Он молчал почти всю дорогу, но потом разговорился и рассказал много интересного о своей поездке и о том, что происходило в городе за последнее время."
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "Я сегодня случайно встретил И. Иванова на вокзале, и мы немного поговорили о погоде, о работе и о наших общих знакомых, которых не видели уже много лет.",
                "Он молчал почти всю дорогу, но потом разговорился и рассказал много интересного о своей поездке и о том, что происходило в городе за последнее время."
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }

    @Test
    fun abbreviation_td_doesNotSplit() {
        val input = "Он очень любил сезонные фрукты: яблоки, груши, сливы, персики, абрикосы, виноград и т.д. И ушёл он из дома только после того, как съел большую тарелку с этими фруктами, выпил стакан свежевыжатого яблочного сока и поговорил с матерью о планах на выходные, потому что она всегда любила такие долгие семейные разговоры."
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "Он очень любил сезонные фрукты: яблоки, груши, сливы, персики, абрикосы, виноград и т.д.",
                "И ушёл он из дома только после того, как съел большую тарелку с этими фруктами, выпил стакан свежевыжатого яблочного сока и поговорил с матерью о планах на выходные, потому что она всегда любила такие долгие семейные разговоры."
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }

    @Test
    fun number_decimal_doesNotSplit() {
        val input = "В математике и физике значение числа π равно 3.14, и это число используется повсюду, от простых школьных задач до самых сложных инженерных и космических расчётов. Это важно для всех, кто изучает точные науки и применяет их на практике, потому что без этого числа невозможно представить современную физику и технику."
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "В математике и физике значение числа π равно 3.14, и это число используется повсюду, от простых школьных задач до самых сложных инженерных и космических расчётов.",
                "Это важно для всех, кто изучает точные науки и применяет их на практике, потому что без этого числа невозможно представить современную физику и технику."
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }

    @Test
    fun number_date_doesNotSplit() {
        val input = "Дата 12.05.2024 была солнечной и тёплой, и многие люди вышли на улицы города, чтобы насладиться по-настоящему хорошей весенней погодой после долгой холодной зимы. Он вышел из дома рано утром, взял с собой термос с чаем и отправился на долгую прогулку по набережной реки в парке."
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "Дата 12.05.2024 была солнечной и тёплой, и многие люди вышли на улицы города, чтобы насладиться по-настоящему хорошей весенней погодой после долгой холодной зимы.",
                "Он вышел из дома рано утром, взял с собой термос с чаем и отправился на долгую прогулку по набережной реки в парке."
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }

    @Test
    fun statusBlock_keyValueLines_notSplit() {
        val input = "Имя: Джон\nУровень: 5\nHP: 120/120\nMP: 100/100\nСила: 15\nЛовкость: 12\nИнтеллект: 18\nМудрость: 14\nХаризма: 11\nВыносливость: 20\nСкорость: 16\nУдача: 8\nЗдоровье: 120\nМана: 90\nОпыт: 3500\nЗолото: 450\nАтака: 30\nЗащита: 25\nСопротивление огню: 5\nСопротивление холоду: 5\nСопротивление яду: 10\nРегенерация: 2"
        assertTrue(input.length > 250)
        assertEquals(listOf(input), SentenceSplitter.splitParagraph(input))
    }

    @Test
    fun statusBlock_delimiterLines_notSplit() {
        val input = "Глава первая: Встреча\n***\nОн шёл домой и думал о том, что произошло сегодня на работе, и не мог найти ответа на вопрос, который мучил его весь вечер и не давал покоя, даже когда он зашёл в свой тёмный подъезд и стал медленно подниматься по лестнице, шаг за шагом, стараясь не думать о плохом"
        assertTrue(input.length > 250)
        assertEquals(listOf(input), SentenceSplitter.splitParagraph(input))
    }

    @Test
    fun dashDialogue_notSplit() {
        val input = "— Привет, как дела? — спросил он, подходя ближе и протягивая руку для рукопожатия. — Хорошо, спасибо, — ответила она и улыбнулась, глядя ему в глаза. — Отлично, тогда пойдём прогуляемся по парку, — предложил он и показал рукой в сторону аллеи. — С удовольствием, — согласилась она, поправляя шарф."
        assertTrue(input.length > 250)
        assertEquals(listOf(input), SentenceSplitter.splitParagraph(input))
    }

    @Test
    fun japanese_splitsIntoThreeSentences() {
        val input = "彼は遅い時間に仕事から家に帰って、とても疲れていたので、まずは冷たいお風呂に入って体を温めて、それから簡単な夕食を食べて、ベッドに入って、長い一日の出来事を一つずつ思い出しながら、明日の朝のことをもう考えないようにして、ただ静かに眠りについた。太陽がゆっくりと西の地平線の向こうに沈んで、空が美しいオレンジ色と紫色に染まって、街の灯りが一つずつ点いていき、そして夜の気配が静かに近づいてきた、まさにそのときに。鳥たちが高い木の上で楽しそうに鳴いていて、その澄んだ声がだんだんと静まりかけた夜の空気の中に優しく響いていた、そして遠くの森からは夜の音が聞こえてきた。"
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "彼は遅い時間に仕事から家に帰って、とても疲れていたので、まずは冷たいお風呂に入って体を温めて、それから簡単な夕食を食べて、ベッドに入って、長い一日の出来事を一つずつ思い出しながら、明日の朝のことをもう考えないようにして、ただ静かに眠りについた。",
                "太陽がゆっくりと西の地平線の向こうに沈んで、空が美しいオレンジ色と紫色に染まって、街の灯りが一つずつ点いていき、そして夜の気配が静かに近づいてきた、まさにそのときに。",
                "鳥たちが高い木の上で楽しそうに鳴いていて、その澄んだ声がだんだんと静まりかけた夜の空気の中に優しく響いていた、そして遠くの森からは夜の音が聞こえてきた。"
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }

    @Test
    fun arabic_splitsIntoThreeSentences() {
        val input = "ذهب الرجل إلى المنزل بعد يوم طويل من العمل في المدينة المزدحمة، حيث كانت الشوارع مليئة بالناس والسيارات والأصوات العالية في كل مكان. هل أنت بخير اليوم بعد هذا اليوم الطويل والمتعب في العمل؟ نعم أنا بخير، شكراً لسؤالك اللطيف، لقد كنت متعباً فقط لأنني مشيت كثيراً اليوم في شوارع المدينة الكبيرة."
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "ذهب الرجل إلى المنزل بعد يوم طويل من العمل في المدينة المزدحمة، حيث كانت الشوارع مليئة بالناس والسيارات والأصوات العالية في كل مكان.",
                "هل أنت بخير اليوم بعد هذا اليوم الطويل والمتعب في العمل؟",
                "نعم أنا بخير، شكراً لسؤالك اللطيف، لقد كنت متعباً فقط لأنني مشيت كثيراً اليوم في شوارع المدينة الكبيرة."
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }

    @Test
    fun devanagari_splitsIntoThreeSentences() {
        val input = "वह देर शाम को अपने घर लौटा और दरवाज़े पर खड़े होकर एक लंबी साँस ली, क्योंकि आज का दिन बहुत थका देने वाला था और उसे बहुत कुछ करना था। सूरज धीरे-धीरे पहाड़ियों के पीछे डूब गया और आसमान लाल और नारंगी रंगों से भर गया, और शाम की ठंडी हवा चलने लगी। पक्षी अपने घोंसलों में लौटकर मीठे स्वर में गाने लगे और पूरे गाँव में शांति छा गई, जबकि घरों में रोशनी एक-एक करके जलने लगी और हवा में ठंडक बढ़ने लगी।"
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "वह देर शाम को अपने घर लौटा और दरवाज़े पर खड़े होकर एक लंबी साँस ली, क्योंकि आज का दिन बहुत थका देने वाला था और उसे बहुत कुछ करना था।",
                "सूरज धीरे-धीरे पहाड़ियों के पीछे डूब गया और आसमान लाल और नारंगी रंगों से भर गया, और शाम की ठंडी हवा चलने लगी।",
                "पक्षी अपने घोंसलों में लौटकर मीठे स्वर में गाने लगे और पूरे गाँव में शांति छा गई, जबकि घरों में रोशनी एक-एक करके जलने लगी और हवा में ठंडक बढ़ने लगी।"
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }

    @Test
    fun lowercaseWordAfterQuestionMark_notSplit() {
        val input = "Что? спросил он тихо и посмотрел на старый дом, стоящий в конце улицы, но никто не ответил ему, и тогда он решил сам войти внутрь и осмотреть все комнаты, где когда-то жила его семья и где теперь лишь пыль лежала на подоконниках да старые портреты висели на стенах, и память о тех временах медленно возвращалась к нему по мере того, как он шёл по скрипучим половицам старого коридора"
        assertTrue(input.length > 250)
        assertEquals(listOf(input), SentenceSplitter.splitParagraph(input))
    }

    @Test
    fun japaneseBrackets_quoteContentKeptWhole() {
        val input = "「彼は言った。大丈夫だ。」 そして彼は静かに部屋を出て、暗い廊下を歩きながら、明日のことを考えていた。電車はもうホームに来ていて、たくさんの人々が慌ただしく乗り降りしていた。彼は一番後ろの席に座って窓の外を見た。景色が次々と流れていくのを眺めながら、彼は深く息を吸った。それから目を閉じて、長い一日の出来事を一つずつ思い出していた。電車の音が心地よく響いて、彼の心は次第に落ち着いていった。そして、窓の外の暗い景色を見ながら、彼は明日からの新しい生活について考え始めた。そのとき、彼のスマートフォンが震えて、彼は画面を確認したが、それはただの通知だった。彼は安堵のため息をついて、もう一度窓の外を見た。"
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "「彼は言った。大丈夫だ。」",
                "そして彼は静かに部屋を出て、暗い廊下を歩きながら、明日のことを考えていた。",
                "電車はもうホームに来ていて、たくさんの人々が慌ただしく乗り降りしていた。",
                "彼は一番後ろの席に座って窓の外を見た。",
                "景色が次々と流れていくのを眺めながら、彼は深く息を吸った。",
                "それから目を閉じて、長い一日の出来事を一つずつ思い出していた。",
                "電車の音が心地よく響いて、彼の心は次第に落ち着いていった。",
                "そして、窓の外の暗い景色を見ながら、彼は明日からの新しい生活について考え始めた。",
                "そのとき、彼のスマートフォンが震えて、彼は画面を確認したが、それはただの通知だった。",
                "彼は安堵のため息をついて、もう一度窓の外を見た。"
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }

    @Test
    fun needsSplitting_short_returnsFalse() {
        assertFalse(SentenceSplitter.needsSplitting("Привет. Как дела?"))
        assertFalse(SentenceSplitter.needsSplitting("Short text without terminator"))
    }

    @Test
    fun needsSplitting_longMultiSentence_returnsTrue() {
        assertTrue(
            SentenceSplitter.needsSplitting(
                "He walked through the quiet streets of the old town, watching the lights flicker on in the windows of the houses he passed one by one in the cold evening air. The sun was setting slowly behind the distant hills, painting the sky in warm shades of orange, pink and deep purple."
            )
        )
    }

    @Test
    fun questWindow_statusBlock_keptWhole() {
        val input = "Окно квеста открылось.\n" +
            "Название: «Побег из подземелья» — вам предстоит выбраться из катакомб, избегая ловушек и патрулей стражи.\n" +
            "Цель: найти выход и вернуться в город до рассвета.\n" +
            "Награда: 5000 опыта и сундук с редким лутом.\n" +
            "Штраф: потеря 10 опыта при провале.\n" +
            "Время: ограничено.\n" +
            "Место: Подземелье старого форта.\n" +
            "Уровень: 15.\n" +
            "Сложность: среднея."
        assertTrue(input.length > 250)
        assertEquals(listOf(input), SentenceSplitter.splitParagraph(input))
    }

    @Test
    fun streamTicker_longValueLines_keptWhole() {
        val input = "Viewers: 1342 watchers and growing.\n" +
            "Chat: user123 says hello and asks about the next episode and whether the stream will continue tomorrow as usual at the same time of the day.\n" +
            "Donate: 500 coins from an anonymous fan who just joined and sent a message to the whole channel and to everyone watching right now."
        assertTrue(input.length > 250)
        assertEquals(listOf(input), SentenceSplitter.splitParagraph(input))
    }

    @Test
    fun adjacentQuestionExclamation_noDanglingSegment() {
        val input = "Она закричала: как ты мог?! Он же обещал, что придёт вовремя и всё объяснит и принесёт цветы, но так и не появился в тот вечер. Она ждала его до поздней ночи у окна, глядя на пустую улицу и на телефон, но он так и не позвонил, и она ушла спать одна, оставив свет гореть в прихожей."
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "Она закричала: как ты мог?!",
                "Он же обещал, что придёт вовремя и всё объяснит и принесёт цветы, но так и не появился в тот вечер.",
                "Она ждала его до поздней ночи у окна, глядя на пустую улицу и на телефон, но он так и не позвонил, и она ушла спать одна, оставив свет гореть в прихожей."
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }

    @Test
    fun adjacentPeriods_noDanglingSegment() {
        val input = "Он шёл по пустому коридору, остановился на секунду и задумался.... Потом медленно пошёл дальше и свернул за угол, где было темно, и остановился там, прислушиваясь к каждому звуку в старом доме. Он думал о том, что случилось раньше вечером, и о том, что ему теперь делать дальше, но так и не нашёл ответа."
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "Он шёл по пустому коридору, остановился на секунду и задумался....",
                "Потом медленно пошёл дальше и свернул за угол, где было темно, и остановился там, прислушиваясь к каждому звуку в старом доме.",
                "Он думал о том, что случилось раньше вечером, и о том, что ему теперь делать дальше, но так и не нашёл ответа."
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }

    @Test
    fun abbreviation_russian_ch_digit_doesNotSplit() {
        val input = "Он читал со скучающим видом, остановился на главе ч. 2 и начал читать вслух, не обращая внимания на скрип старой двери и на тихие шаги за спиной. Она слушала внимательно, кивая в такт каждому слову, и думала о том, что он читает слишком быстро, но не решалась перебить его."
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "Он читал со скучающим видом, остановился на главе ч. 2 и начал читать вслух, не обращая внимания на скрип старой двери и на тихие шаги за спиной.",
                "Она слушала внимательно, кивая в такт каждому слову, и думала о том, что он читает слишком быстро, но не решалась перебить его."
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }

    @Test
    fun abbreviation_russian_d_houseNumber_doesNotSplit() {
        val input = "Он жил на улице Ленина, д. 5 и каждый день ходил мимо этого старого дома на углу, и часто задумывался о том, кто мог жить здесь раньше, почему уехал и что он здесь искал. Однажды он всё-таки решил остановиться и заглянуть внутрь, но дверь оказалась заперта, и он ушёл ни с чем, так и не узнав, что скрывалось за той старой дверью."
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "Он жил на улице Ленина, д. 5 и каждый день ходил мимо этого старого дома на углу, и часто задумывался о том, кто мог жить здесь раньше, почему уехал и что он здесь искал.",
                "Однажды он всё-таки решил остановиться и заглянуть внутрь, но дверь оказалась заперта, и он ушёл ни с чем, так и не узнав, что скрывалось за той старой дверью."
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }

    @Test
    fun abbreviation_etcP_comma_doesNotSplit() {
        val input = "Он любил фрукты: яблоки, груши, сливы, апельсины, персики, абрикосы, виноград и т. п., потому что это было вкусно и полезно для здоровья, и он ел их каждый день без исключения, начиная с ранней весны и до самой поздней осени. Она предпочитала овощи, свежие и хрустящие, и готовила из них салаты почти каждый вечер, добавляя туда свежую зелень, огурцы, помидоры и немного оливкового масла."
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "Он любил фрукты: яблоки, груши, сливы, апельсины, персики, абрикосы, виноград и т. п., потому что это было вкусно и полезно для здоровья, и он ел их каждый день без исключения, начиная с ранней весны и до самой поздней осени.",
                "Она предпочитала овощи, свежие и хрустящие, и готовила из них салаты почти каждый вечер, добавляя туда свежую зелень, огурцы, помидоры и немного оливкового масла."
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }

    @Test
    fun abbreviation_german_z_doesNotSplit() {
        val input = "В том магазине продавалось снаряжение для любого героя, например, z. B. меч или щит с красивой гравировкой, и покупатели могли выбрать то, что им подходило больше всего, сравнивая цены и качество между витринами. Он выбрал меч, потому что он был лёгким и удобным, и носил его с гордостью, показывая остальным героям, как он блестит на солнце."
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "В том магазине продавалось снаряжение для любого героя, например, z. B. меч или щит с красивой гравировкой, и покупатели могли выбрать то, что им подходило больше всего, сравнивая цены и качество между витринами.",
                "Он выбрал меч, потому что он был лёгким и удобным, и носил его с гордостью, показывая остальным героям, как он блестит на солнце."
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }

    @Test
    fun closingQuote_followedByDash_doesNotSplit() {
        val input = "«Беги!» — крикнул он и побежал к выходу, лавируя между машинами и оглядываясь назад на каждого прохожего, который попадался ему на пути, пока бежал через парковку. «Стой!» — закричала она ему вслед. Он не остановился и даже не замедлил шаг, и вскоре скрылся из виду за поворотом, и она осталась стоять одна на пустой улице, тяжело дыша и сжимая в руке ключи."
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "«Беги!» — крикнул он и побежал к выходу, лавируя между машинами и оглядываясь назад на каждого прохожего, который попадался ему на пути, пока бежал через парковку.",
                "«Стой!» — закричала она ему вслед.",
                "Он не остановился и даже не замедлил шаг, и вскоре скрылся из виду за поворотом, и она осталась стоять одна на пустой улице, тяжело дыша и сжимая в руке ключи."
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }

    @Test
    fun multilineDashDialogue_keptWhole() {
        val input = "Он оглянулся на неё и сказал, что всё будет хорошо, что он обязательно вернётся до темноты, что она не должна волноваться и может спокойно ждать его дома с ужином на столе, и что он любит её.\n" +
            "— А если не получится? — спросила она тихо.\n" +
            "— Получится, — ответил он и улыбнулся."
        assertTrue(input.length > 250)
        assertEquals(listOf(input), SentenceSplitter.splitParagraph(input))
    }

    @Test
    fun systemMessage_bracketedName_threeSegments() {
        val input = "Вы получили навык [Кулинария]! Была выполнена скрытая квестовая награда за то, что вы успешно закончили первое задание на кухне, и открылось новое умение готовки. Вы получили 500 очков опыта, немного серебра и новый рецепт, который теперь можно изучить, открыв меню персонажа."
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "Вы получили навык [Кулинария]!",
                "Была выполнена скрытая квестовая награда за то, что вы успешно закончили первое задание на кухне, и открылось новое умение готовки.",
                "Вы получили 500 очков опыта, немного серебра и новый рецепт, который теперь можно изучить, открыв меню персонажа."
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }

    @Test
    fun chatBlock_nicknameLines_keptWhole() {
        val input = "user123: lul\n" +
            "Boss: !drop 100 gold coins\n" +
            "nik:\n" +
            "nickname2:\n" +
            "player33: ,\n" +
            "admin: ~\n" +
            "moderator: lfg pls\n" +
            "streamer: welcome back to the channel\n" +
            "viewer42: gg wp everyone\n" +
            "raider123: hello hello\n" +
            "lurker77: !lurk\n" +
            "vipMember: thanks for the raid\n" +
            "newcomer: first time here\n" +
            "regular: nice stream today"
        assertTrue(input.length > 250)
        assertEquals(listOf(input), SentenceSplitter.splitParagraph(input))
    }

    @Test
    fun capsParagraph_threeSegments() {
        val input = "ОН ПРИШЁЛ ДОМОЙ ПОСЛЕ ДОЛГОЙ ДОРОГИ И УСТАЛО СЕЛ НА ДИВАН В ПРИХОЖЕЙ. ОН СЕЛ ЗА СТОЛ И ОТКРЫЛ СВОЮ ЛЮБИМУЮ КНИГУ, НО СРАЗУ ЗАДУМАЛСЯ О ТОМ, ЧТО СЛУЧИЛОСЬ ДНЁМ И ОСТАЛОСЬ НЕДОСКАЗАННЫМ. ОНА ЖДАЛА ЕГО С УЖИНОМ НА КУХНЕ, СТАВЯ ТАРЕЛКИ И НАЛИВАЯ ЧАЙ В ЧАШКИ, И ПОГЛЯДЫВАЯ НА ЧАСЫ, ДУМАЯ О ТОМ, КУДА ОН МОГ УЙТИ И КОГДА ВЕРНЁТСЯ ДОМОЙ."
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "ОН ПРИШЁЛ ДОМОЙ ПОСЛЕ ДОЛГОЙ ДОРОГИ И УСТАЛО СЕЛ НА ДИВАН В ПРИХОЖЕЙ.",
                "ОН СЕЛ ЗА СТОЛ И ОТКРЫЛ СВОЮ ЛЮБИМУЮ КНИГУ, НО СРАЗУ ЗАДУМАЛСЯ О ТОМ, ЧТО СЛУЧИЛОСЬ ДНЁМ И ОСТАЛОСЬ НЕДОСКАЗАННЫМ.",
                "ОНА ЖДАЛА ЕГО С УЖИНОМ НА КУХНЕ, СТАВЯ ТАРЕЛКИ И НАЛИВАЯ ЧАЙ В ЧАШКИ, И ПОГЛЯДЫВАЯ НА ЧАСЫ, ДУМАЯ О ТОМ, КУДА ОН МОГ УЙТИ И КОГДА ВЕРНЁТСЯ ДОМОЙ."
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }

    @Test
    fun urlDomain_keptWhole_threeSegments() {
        val input = "Он зашёл на example.com, открыл вкладку с новостями и прочитал несколько статей за сегодняшний день. Потом ушёл на кухню, чтобы заварить себе крепкий чай и сделать бутерброд с сыром. Она ждала его у выхода, надев пальто, взяв ключи от квартиры и поглядывая на уличные часы."
        assertTrue(input.length > 250)
        assertEquals(
            listOf(
                "Он зашёл на example.com, открыл вкладку с новостями и прочитал несколько статей за сегодняшний день.",
                "Потом ушёл на кухню, чтобы заварить себе крепкий чай и сделать бутерброд с сыром.",
                "Она ждала его у выхода, надев пальто, взяв ключи от квартиры и поглядывая на уличные часы."
            ),
            SentenceSplitter.splitParagraph(input)
        )
    }
}