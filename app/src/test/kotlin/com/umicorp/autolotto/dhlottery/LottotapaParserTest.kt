package com.umicorp.autolotto.dhlottery

import org.junit.Assert.assertEquals
import org.junit.Test

class LottotapaParserTest {

    @Test
    fun parseHtmlText_correctlyParsesWinningResults() {
        val sampleText = """
            1246회 로또 당첨번호 (2026-10-17) 16 22 23 24 27 28 + 6 홀짝 2 : 4 저고 : 2 : 4 AC값 : 5 끝수합 : 30 총합 : 140
            1245회 로또 당첨번호 (2026-10-10) 1호기 4 5 25 28 32 42 + 12 홀짝 2 : 4 저고 : 2 : 4 AC값 : 10 끝수합 : 26 총합 : 136
        """.trimIndent()

        val results = LottotapaParser.parseHtmlText(sampleText)

        assertEquals(2, results.size)

        val r1245 = results[0]
        assertEquals(1245, r1245.round)
        assertEquals("2026-10-10", r1245.date)
        assertEquals(listOf(4, 5, 25, 28, 32, 42), r1245.numbers)
        assertEquals(12, r1245.bonus)

        val r1246 = results[1]
        assertEquals(1246, r1246.round)
        assertEquals("2026-10-17", r1246.date)
        assertEquals(listOf(16, 22, 23, 24, 27, 28), r1246.numbers)
        assertEquals(6, r1246.bonus)
    }
}
