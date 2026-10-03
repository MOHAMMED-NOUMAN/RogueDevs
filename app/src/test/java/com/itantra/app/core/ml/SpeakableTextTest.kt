package com.itantra.app.core.ml

import com.itantra.app.core.messaging.MessageLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakableTextTest {

    private fun en(text: String) = SpeakableText.chunks(text, MessageLanguage.ENGLISH)
    private fun hi(text: String) = SpeakableText.chunks(text, MessageLanguage.HINDI)

    @Test
    fun `english numbers are spelled out`() {
        assertEquals(listOf("two people near gate forty five"), en("2 people near gate 45"))
        assertEquals(listOf("one hundred kits"), en("100 kits"))
        assertEquals(listOf("three thousand four hundred twelve metres"), en("3412 metres"))
    }

    @Test
    fun `phone numbers and leading zeros are read digit by digit`() {
        assertEquals(listOf("call nine eight seven six five four three two one zero"), en("call 9876543210"))
        assertEquals(listOf("room zero seven"), en("room 07"))
    }

    @Test
    fun `hindi numbers use hindi words and indian grouping`() {
        assertEquals(listOf("दो लोग"), hi("2 लोग"))
        assertEquals(listOf("पैंतालीस मिनट"), hi("४५ मिनट"))
        assertEquals(listOf("एक लाख पच्चीस हज़ार"), hi("125000"))
    }

    @Test
    fun `precomposed nukta letters are split into letter plus nukta`() {
        // "पेड़" typed with the single code point U+095C (ड़).
        assertEquals(listOf("पेड़"), hi("पेड़"))
    }

    @Test
    fun `sentences become separate chunks`() {
        assertEquals(listOf("Help.", "We are at the gate!"), en("Help. We are at the gate!"))
        assertEquals(listOf("मदद करो।", "हम गेट पर हैं"), hi("मदद करो। हम गेट पर हैं"))
    }

    @Test
    fun `decimal points are spoken, not treated as sentence ends`() {
        assertEquals(listOf("two point five km"), en("2.5 km"))
        assertEquals(listOf("दो दशमलव पाँच किलोमीटर"), hi("2.5 किलोमीटर"))
    }

    @Test
    fun `long sentences are cut at spaces within the limit`() {
        val text = "water ".repeat(80).trim()
        val chunks = en(text)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= SpeakableText.MAX_CHUNK })
        assertEquals(text, chunks.joinToString(" "))
    }

    @Test
    fun `blank text has nothing to say`() {
        assertEquals(emptyList<String>(), en("  \n "))
    }
}
