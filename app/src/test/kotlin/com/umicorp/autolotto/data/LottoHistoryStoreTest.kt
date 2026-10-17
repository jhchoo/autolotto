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
}
