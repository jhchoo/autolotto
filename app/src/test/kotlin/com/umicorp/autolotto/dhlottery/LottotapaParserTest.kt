package com.umicorp.autolotto.dhlottery

import org.junit.Assert.assertEquals
import org.junit.Test

class LottotapaParserTest {

    @Test
    fun parseHtml_parsesLottotapaDomStructureCorrectly() {
        val sampleHtml = """
            <html>
            <body>
                <div>
                    <h2>10회 로또 당첨번호 2003-02-08</h2>
                    <ul>
                        <li>9</li>
                        <li>13</li>
                        <li>21</li>
                        <li>25</li>
                        <li>32</li>
                        <li>42</li>
                        <li>+</li>
                        <li>2</li>
                    </ul>
                </div>
                <div>
                    <h2>9회 로또 당첨번호 2003-02-01</h2>
                    <ul>
                        <li>2</li>
                        <li>4</li>
                        <li>16</li>
                        <li>17</li>
                        <li>36</li>
                        <li>39</li>
                        <li>+</li>
                        <li>14</li>
                    </ul>
                </div>
            </body>
            </html>
        """.trimIndent()

        val results = LottotapaParser.parseHtml(sampleHtml)

        assertEquals(2, results.size)

        val r9 = results.first { it.round == 9 }
        assertEquals(9, r9.round)
        assertEquals("2003-02-01", r9.date)
        assertEquals(listOf(2, 4, 16, 17, 36, 39), r9.numbers)
        assertEquals(14, r9.bonus)

        val r10 = results.first { it.round == 10 }
        assertEquals(10, r10.round)
        assertEquals("2003-02-08", r10.date)
        assertEquals(listOf(9, 13, 21, 25, 32, 42), r10.numbers)
        assertEquals(2, r10.bonus)
    }

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
