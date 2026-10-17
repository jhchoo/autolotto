package com.umicorp.autolotto.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LottoDatabaseTest {

    @Test
    fun winningResult_rangeCalculationLogic() {
        val sampleList = listOf(
            WinningResult(1246, listOf(16, 22, 23, 24, 27, 28), 6, "2026-10-17"),
            WinningResult(1245, listOf(4, 5, 25, 28, 32, 42), 12, "2026-10-10"),
            WinningResult(1244, listOf(1, 13, 18, 26, 34, 38), 25, "2026-10-03"),
        )

        val filtered = sampleList.filter { it.round in 1244..1245 }.sortedByDescending { it.round }

        assertEquals(2, filtered.size)
        assertEquals(1245, filtered[0].round)
        assertEquals(1244, filtered[1].round)
    }
}
