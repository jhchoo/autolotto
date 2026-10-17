package com.umicorp.autolotto.generator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SmartNumberGeneratorTest {

    @Test
    fun generateNumbers_returns6UniqueNumbersInAscendingOrder() {
        val numbers = SmartNumberGenerator.generateNumbers(LottoStrategy.BALANCED)

        assertEquals(6, numbers.size)
        assertEquals(6, numbers.toSet().size)
        assertEquals(numbers.sorted(), numbers)
        assertTrue(numbers.all { it in 1..45 })
    }

    @Test
    fun generateNumbers_satisfiesBalancedConstraints() {
        val strategy = LottoStrategy.BALANCED
        val random = Random(42) // 시드 고정
        val numbers = SmartNumberGenerator.generateNumbers(strategy, random = random)

        assertTrue(SmartNumberGenerator.validateAllConstraints(numbers, strategy))

        val oddCount = numbers.count { it % 2 != 0 }
        val evenCount = 6 - oddCount
        assertTrue((oddCount to evenCount) in strategy.oddEvenRatios)

        val lowCount = numbers.count { it in 1..20 }
        val highCount = 6 - lowCount
        assertTrue((lowCount to highCount) in strategy.highLowRatios)

        val sum = numbers.sum()
        assertTrue(sum in strategy.minSum..strategy.maxSum)
    }

    @Test
    fun isValidOddEvenRatio_validatesCorrectly() {
        assertTrue(SmartNumberGenerator.isValidOddEvenRatio(listOf(1, 3, 5, 2, 4, 6))) // 3:3
        assertTrue(SmartNumberGenerator.isValidOddEvenRatio(listOf(1, 3, 5, 7, 2, 4))) // 4:2
        assertEquals(false, SmartNumberGenerator.isValidOddEvenRatio(listOf(1, 2, 4, 6, 8, 10))) // 1:5 -> false (default ratios 3:3, 4:2, 2:4)
    }

    @Test
    fun isValidLastDigits_validatesMaxSameLastDigit() {
        assertTrue(SmartNumberGenerator.isValidLastDigits(listOf(7, 17, 24, 31, 42, 45), maxSame = 2)) // 7,17 (2개)
        assertEquals(false, SmartNumberGenerator.isValidLastDigits(listOf(7, 17, 27, 31, 42, 45), maxSame = 2)) // 7,17,27 (3개) -> false
    }

    @Test
    fun isValidConsecutive_validatesMaxConsecutiveNumbers() {
        assertTrue(SmartNumberGenerator.isValidConsecutive(listOf(10, 11, 20, 25, 30, 40), maxConsecutive = 2)) // 10,11 (2개 연속)
        assertEquals(false, SmartNumberGenerator.isValidConsecutive(listOf(10, 11, 12, 25, 30, 40), maxConsecutive = 2)) // 10,11,12 (3개 연속) -> false
    }

    @Test
    fun generateNumbers_multipleRunsAlwaysSucceed() {
        for (i in 1..50) {
            val numbers = SmartNumberGenerator.generateNumbers(LottoStrategy.BALANCED)
            assertEquals(6, numbers.size)
            assertTrue(numbers.all { it in 1..45 })
        }
    }
}
