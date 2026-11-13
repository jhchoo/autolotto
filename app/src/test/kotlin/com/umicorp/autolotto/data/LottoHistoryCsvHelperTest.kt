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
                prize2nd = 44763437L,
                prize3rd = 1447265L,
            ),
            WinningResult(
                round = 1151,
                numbers = listOf(1, 8, 15, 23, 31, 40),
                bonus = 42,
                date = "2024-12-21",
                prize1st = 2100000000L,
                prize2nd = 50000000L,
                prize3rd = 1500000L,
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
        assertEquals(44763437L, r1.prize2nd)
        assertEquals(1447265L, r1.prize3rd)

        val r2 = parsed.first { it.round == 1151 }
        assertEquals(listOf(1, 8, 15, 23, 31, 40), r2.numbers)
        assertEquals(42, r2.bonus)
        assertEquals("2024-12-21", r2.date)
        assertEquals(2100000000L, r2.prize1st)
    }

    @Test
    fun parseCsv_handlesHeaderlessAndNoPrizeFormats() {
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
