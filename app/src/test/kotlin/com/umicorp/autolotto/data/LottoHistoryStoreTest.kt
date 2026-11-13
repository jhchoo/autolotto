package com.umicorp.autolotto.data

import com.umicorp.autolotto.generator.LottoFrequencyAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LottoHistoryStoreTest {

    @Test
    fun frequencyAnalyzer_handlesResultsMapCorrectly() {
        val sampleMap = mapOf(
            1200 to WinningResult(1200, listOf(1, 2, 3, 4, 5, 6), 7, "2025-01-01"),
            1201 to WinningResult(1201, listOf(1, 2, 3, 10, 20, 30), 40, "2025-01-08"),
        )

        val analysis = LottoFrequencyAnalyzer.analyze(sampleMap)

        assertEquals(1200, analysis.minRound)
        assertEquals(1201, analysis.maxRound)
        assertEquals(2, analysis.totalRounds)
        assertEquals(45, analysis.frequencies.size)

        // 번호 1, 2, 3은 각각 2회 출현
        val f1 = analysis.frequencies.first { it.number == 1 }
        val f2 = analysis.frequencies.first { it.number == 2 }
        val f3 = analysis.frequencies.first { it.number == 3 }
        assertEquals(2, f1.count)
        assertEquals(2, f2.count)
        assertEquals(2, f3.count)
    }

    @Test
    fun storeCache_maintainsMemoryStateAndQueries() {
        val sampleMap = mapOf(
            1150 to WinningResult(
                round = 1150,
                numbers = listOf(3, 4, 12, 18, 24, 37),
                bonus = 45,
                date = "2024-12-14",
                prize1st = 2641042782L,
                winnerCount1st = 10,
            ),
            1151 to WinningResult(
                round = 1151,
                numbers = listOf(1, 8, 15, 23, 31, 40),
                bonus = 42,
                date = "2024-12-21",
                prize1st = 2100000000L,
                winnerCount1st = 12,
            ),
        )
        LottoHistoryStore.setGlobalWinningResults(sampleMap)

        assertEquals(2, LottoHistoryStore.getTotalCount())
        assertEquals(1151, LottoHistoryStore.getLatestStoredRound())
        assertEquals(sampleMap, LottoHistoryStore.globalWinningResults)

        val range = LottoHistoryStore.getWinningResultsRange(startRound = 1150, endRound = 1151)
        assertEquals(2, range.size)
        assertEquals(1151, range[0].round)
        assertEquals(1150, range[1].round)
        assertEquals(12, range[0].winnerCount1st)
        assertEquals(10, range[1].winnerCount1st)
    }
}
