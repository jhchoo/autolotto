package com.umicorp.autolotto.generator

import com.umicorp.autolotto.data.WinningResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LottoFrequencyAnalyzerTest {

    @Test
    fun analyze_correctlyComputesFrequencies() {
        val history = (1200..1210).associateWith { round ->
            WinningResult(
                round = round,
                numbers = listOf(1, 2, 3, 4, 5, round % 45 + 1).sorted(),
                bonus = 7,
                date = "2025-01-01",
            )
        }

        val analysis = LottoFrequencyAnalyzer.analyze(history)

        assertEquals(1200, analysis.minRound)
        assertEquals(1210, analysis.maxRound)
        assertEquals(11, analysis.totalRounds)

        assertEquals(45, analysis.frequencies.size)
        assertEquals(15, analysis.hotNumbers.size)
        assertEquals(15, analysis.coldNumbers.size)
        assertEquals(15, analysis.normalNumbers.size)

        // 모든 45개 번호가 세 그룹 중 정확히 하나에 들어감
        val allCategorized = (analysis.hotNumbers + analysis.coldNumbers + analysis.normalNumbers).toSet()
        assertEquals(45, allCategorized.size)

        // 최다 출현 TOP 5 확인
        val top5 = analysis.topFrequentNumbers(5)
        assertEquals(5, top5.size)
        assertTrue(top5[0].second >= top5[1].second)
    }
}
