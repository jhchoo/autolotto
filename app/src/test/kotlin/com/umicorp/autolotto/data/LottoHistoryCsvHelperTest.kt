package com.umicorp.autolotto.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LottoHistoryCsvHelperTest {

    @Test
    fun toCsvAndParseCsv_roundTripMaintainsDataIntegrity() {
        val sampleResults = listOf(
            WinningResult(
                round = 1150,
                numbers = listOf(3, 4, 12, 18, 24, 37),
                bonus = 45,
                date = "2024-12-14",
                prize1st = 2641042782L,
                winnerCount1st = 10,
            ),
            WinningResult(
                round = 1151,
                numbers = listOf(1, 8, 15, 23, 31, 40),
                bonus = 42,
                date = "2024-12-21",
                prize1st = 2100000000L,
                winnerCount1st = 12,
            ),
        )

        val csvString = LottoHistoryCsvHelper.toCsv(sampleResults)
        val parsed = LottoHistoryCsvHelper.parseCsv(csvString)

        assertEquals(2, parsed.size)

        val r1 = parsed.first { it.round == 1150 }
        assertEquals(listOf(3, 4, 12, 18, 24, 37), r1.numbers)
        assertEquals(45, r1.bonus)
        assertEquals("2024-12-14", r1.date)
        assertEquals(2641042782L, r1.prize1st)
        assertEquals(10, r1.winnerCount1st)

        val r2 = parsed.first { it.round == 1151 }
        assertEquals(listOf(1, 8, 15, 23, 31, 40), r2.numbers)
        assertEquals(42, r2.bonus)
        assertEquals("2024-12-21", r2.date)
        assertEquals(2100000000L, r2.prize1st)
        assertEquals(12, r2.winnerCount1st)
    }

    @Test
    fun parseCsv_parsesUserWinningNumbersFormatWithQuotesAndCommas() {
        val rawCsv = """
            회차,번호1,번호2,번호3,번호4,번호5,번호6,보너스,1등 당첨금,1등 당첨수
            1249,2,11,16,25,34,35,14,"2,025,045,509",14
            1248,16,18,27,36,38,42,22,"1,933,658,450",15
            1,10,23,29,33,37,40,16,0,0
        """.trimIndent()

        val parsed = LottoHistoryCsvHelper.parseCsv(rawCsv)
        assertEquals(3, parsed.size)

        val r1249 = parsed.first { it.round == 1249 }
        assertEquals(listOf(2, 11, 16, 25, 34, 35), r1249.numbers)
        assertEquals(14, r1249.bonus)
        assertEquals(2025045509L, r1249.prize1st)
        assertEquals(14, r1249.winnerCount1st)
        assertEquals("2026-11-07", r1249.date)

        val r1 = parsed.first { it.round == 1 }
        assertEquals(listOf(10, 23, 29, 33, 37, 40), r1.numbers)
        assertEquals(16, r1.bonus)
        assertEquals(0L, r1.prize1st)
        assertEquals(0, r1.winnerCount1st)
        assertEquals("2002-12-07", r1.date)
    }

    @Test
    fun parseCsv_handlesHeaderlessAndDateFormats() {
        val rawCsv = """
            1100, 2024-01-01, 5, 12, 19, 25, 33, 44, 10
            1101, 2024-01-08, 1, 2, 3, 4, 5, 6, 7, 1000000
        """.trimIndent()

        val parsed = LottoHistoryCsvHelper.parseCsv(rawCsv)
        assertEquals(2, parsed.size)

        val r1 = parsed[0]
        assertEquals(1100, r1.round)
        assertEquals("2024-01-01", r1.date)
        assertEquals(listOf(5, 12, 19, 25, 33, 44), r1.numbers)
        assertEquals(10, r1.bonus)
        assertEquals(0L, r1.prize1st)

        val r2 = parsed[1]
        assertEquals(1101, r2.round)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), r2.numbers)
        assertEquals(7, r2.bonus)
        assertEquals(1000000L, r2.prize1st)
    }

    @Test
    fun globalCache_queriedCorrectlyWithoutContext() {
        val sampleMap = mapOf(
            1200 to WinningResult(1200, listOf(1, 2, 3, 4, 5, 6), 7, "2025-01-01", 1000L),
            1201 to WinningResult(1201, listOf(7, 8, 9, 10, 11, 12), 13, "2025-01-08", 2000L),
            1202 to WinningResult(1202, listOf(13, 14, 15, 16, 17, 18), 19, "2025-01-15", 3000L),
        )

        LottoHistoryStore.setGlobalWinningResults(sampleMap)

        assertEquals(sampleMap, LottoHistoryStore.globalWinningResults)
        assertEquals(1202, LottoHistoryStore.getLatestStoredRound())
        assertEquals(3, LottoHistoryStore.getTotalCount())

        val range = LottoHistoryStore.getWinningResultsRange(startRound = 1201, endRound = 1202)
        assertEquals(2, range.size)
        assertEquals(1202, range[0].round)
        assertEquals(1201, range[1].round)

        val allResults = LottoHistoryStore.getAllWinningResults()
        assertEquals(3, allResults.size)
    }
}
