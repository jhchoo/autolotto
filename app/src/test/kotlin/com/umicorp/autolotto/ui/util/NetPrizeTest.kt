package com.umicorp.autolotto.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NetPrizeTest {

    @Test
    fun calculateNetPrize_zeroOrNegative() {
        assertEquals(0L, calculateNetPrize(0L))
        assertEquals(0L, calculateNetPrize(-1000L))
    }

    @Test
    fun calculateNetPrize_underOrEqual300Million() {
        // 1억원: 22% 공제 후 7800만원
        assertEquals(78_000_000L, calculateNetPrize(100_000_000L))
        // 3억원: 22% 공제 후 2억 3400만원
        assertEquals(234_000_000L, calculateNetPrize(300_000_000L))
    }

    @Test
    fun calculateNetPrize_over300Million() {
        // 20억원: 3억까지 78%(2억 3400만) + 17억 67%(11억 3900만) = 13억 7300만원
        assertEquals(1_373_000_000L, calculateNetPrize(2_000_000_000L))
    }

    @Test
    fun formatNetPrizeUnit_formatting() {
        assertEquals("", formatNetPrizeUnit(0L))
        assertEquals("4000만원", formatNetPrizeUnit(40_000_000L))
        assertEquals("1억원", formatNetPrizeUnit(78_000_000L))
        assertEquals("14억원", formatNetPrizeUnit(1_373_000_000L))
    }
}
