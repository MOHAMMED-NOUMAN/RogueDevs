package com.itantra.app.core.ml

import com.itantra.app.core.messaging.MessageLanguage
import java.text.Normalizer

/**
 * Turns a message into what the voice can say: numbers spelled out (the voices have no
 * reliable digit symbols, so "2 people" would otherwise lose the "2"), Hindi letters with a
 * nukta split into letter + nukta (the Hindi voice only knows the split form), and the text
 * cut into sentences of at most [MAX_CHUNK] characters, each synthesized on its own.
 */
object SpeakableText {
    /** Longest piece synthesized in one call; longer input costs more memory and latency. */
    const val MAX_CHUNK = 200

    fun chunks(text: String, language: MessageLanguage): List<String> =
        SENTENCE_END.split(text)
            .map { spoken(it, language).replace(SPACES, " ").trim() }
            .filter { it.isNotEmpty() }
            .flatMap { wrap(it) }

    private fun spoken(sentence: String, language: MessageLanguage): String {
        val ascii = sentence.map { c -> if (c in '०'..'९') '0' + (c - '०') else c }.joinToString("")
        val point = when (language) {
            MessageLanguage.ENGLISH -> " point "
            MessageLanguage.HINDI -> " दशमलव "
        }
        val numbers = NUMBER.replace(DECIMAL_POINT.replace(ascii, point)) { match ->
            val number = match.value
            " " + when {
                number.length > MAX_NUMBER_DIGITS || number.length > 1 && number[0] == '0' ->
                    number.map { words(it - '0', language) }.joinToString(" ")
                else -> words(number.toInt(), language)
            } + " "
        }
        // NFC also splits precomposed nukta letters (क़ ड़ ...): they are composition exclusions.
        return Normalizer.normalize(numbers, Normalizer.Form.NFC)
    }

    /** Splits [sentence] at spaces into pieces of at most [MAX_CHUNK] characters. */
    private fun wrap(sentence: String): List<String> {
        val pieces = mutableListOf<String>()
        var rest = sentence
        while (rest.length > MAX_CHUNK) {
            val cut = rest.lastIndexOf(' ', MAX_CHUNK).takeIf { it > 0 } ?: MAX_CHUNK
            pieces += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) pieces += rest
        return pieces
    }

    fun words(n: Int, language: MessageLanguage): String = when (language) {
        MessageLanguage.ENGLISH -> english(n)
        MessageLanguage.HINDI -> hindi(n)
    }

    private fun english(n: Int): String = when {
        n < 20 -> EN_SMALL[n]
        n < 100 -> EN_TENS[n / 10] + if (n % 10 != 0) " " + EN_SMALL[n % 10] else ""
        n < 1_000 -> EN_SMALL[n / 100] + " hundred" + if (n % 100 != 0) " " + english(n % 100) else ""
        n < 1_000_000 -> english(n / 1_000) + " thousand" + if (n % 1_000 != 0) " " + english(n % 1_000) else ""
        else -> english(n / 1_000_000) + " million" + if (n % 1_000_000 != 0) " " + english(n % 1_000_000) else ""
    }

    /** Indian grouping: hazaar (1,000), lakh (1,00,000). Up to [MAX_NUMBER_DIGITS] digits. */
    private fun hindi(n: Int): String = when {
        n < 100 -> HI_UPTO_99[n]
        n < 1_000 -> HI_UPTO_99[n / 100] + " सौ" + if (n % 100 != 0) " " + hindi(n % 100) else ""
        n < 1_00_000 -> hindi(n / 1_000) + " हज़ार" + if (n % 1_000 != 0) " " + hindi(n % 1_000) else ""
        else -> hindi(n / 1_00_000) + " लाख" + if (n % 1_00_000 != 0) " " + hindi(n % 1_00_000) else ""
    }

    /** Longer digit runs (phone numbers, codes) are read digit by digit. */
    private const val MAX_NUMBER_DIGITS = 7

    private val NUMBER = Regex("[0-9]+")
    private val DECIMAL_POINT = Regex("(?<=[0-9])\\.(?=[0-9])")
    private val SENTENCE_END = Regex("(?<=[.!?।])\\s+|\\n+")
    private val SPACES = Regex("\\s+")

    private val EN_SMALL = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val EN_TENS = listOf(
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
    )

    // Hindi numbers below 100 are each their own word.
    private val HI_UPTO_99 = """
        शून्य एक दो तीन चार पाँच छह सात आठ नौ
        दस ग्यारह बारह तेरह चौदह पंद्रह सोलह सत्रह अठारह उन्नीस
        बीस इक्कीस बाईस तेईस चौबीस पच्चीस छब्बीस सत्ताईस अट्ठाईस उनतीस
        तीस इकतीस बत्तीस तैंतीस चौंतीस पैंतीस छत्तीस सैंतीस अड़तीस उनतालीस
        चालीस इकतालीस बयालीस तैंतालीस चवालीस पैंतालीस छियालीस सैंतालीस अड़तालीस उनचास
        पचास इक्यावन बावन तिरेपन चौवन पचपन छप्पन सत्तावन अट्ठावन उनसठ
        साठ इकसठ बासठ तिरेसठ चौंसठ पैंसठ छियासठ सड़सठ अड़सठ उनहत्तर
        सत्तर इकहत्तर बहत्तर तिहत्तर चौहत्तर पचहत्तर छिहत्तर सतहत्तर अठहत्तर उन्यासी
        अस्सी इक्यासी बयासी तिरासी चौरासी पचासी छियासी सत्तासी अट्ठासी नवासी
        नब्बे इक्यानबे बानबे तिरानबे चौरानबे पचानबे छियानबे सत्तानबे अट्ठानबे निन्यानबे
    """.trim().split(SPACES)
}
