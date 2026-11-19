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

        val lowCount = numbers.count { it in 1..22 }
        val highCount = 6 - lowCount
        assertTrue((lowCount to highCount) in strategy.highLowRatios)

        val sum = numbers.sum()
        assertTrue(sum in strategy.minSum..strategy.maxSum)
        assertTrue(sum in 115..175)
    }

    @Test
    fun isValidOddEvenRatio_validatesCorrectly() {
        assertTrue(SmartNumberGenerator.isValidOddEvenRatio(listOf(1, 3, 5, 2, 4, 6))) // 3:3
        assertTrue(SmartNumberGenerator.isValidOddEvenRatio(listOf(1, 3, 5, 7, 2, 4))) // 4:2
        assertEquals(false, SmartNumberGenerator.isValidOddEvenRatio(listOf(1, 2, 4, 6, 8, 10))) // 1:5 -> false (default ratios 3:3, 4:2, 2:4)
    }

    @Test
    fun isValidMissingDecades_validatesOneOrTwoMissingDecades() {
        // 40번대 1개 구간 전멸 -> true
        assertTrue(SmartNumberGenerator.isValidMissingDecades(listOf(1, 2, 10, 20, 30, 31)))
        // 30번대, 40번대 2개 구간 전멸 -> true
        assertTrue(SmartNumberGenerator.isValidMissingDecades(listOf(1, 2, 10, 11, 20, 21)))
        // 전멸구간 0개 (모든 구간 포함) -> false
        assertEquals(false, SmartNumberGenerator.isValidMissingDecades(listOf(1, 10, 20, 30, 40, 45)))
        // 전멸구간 3개 (20, 30, 40번대 전멸) -> false
        assertEquals(false, SmartNumberGenerator.isValidMissingDecades(listOf(1, 2, 3, 10, 11, 12)))
    }

    @Test
    fun isValidLastDigits_validatesMaxSameLastDigit() {
        assertTrue(SmartNumberGenerator.isValidLastDigits(listOf(7, 17, 27, 31, 42, 45), maxSame = 3)) // 7,17,27 (3개)
        assertEquals(false, SmartNumberGenerator.isValidLastDigits(listOf(7, 17, 27, 37, 42, 45), maxSame = 3)) // 7,17,27,37 (4개) -> false
        // 기본값(3) 검증
        assertTrue(SmartNumberGenerator.isValidLastDigits(listOf(7, 17, 27, 31, 42, 45)))
        assertEquals(false, SmartNumberGenerator.isValidLastDigits(listOf(7, 17, 27, 37, 42, 45)))
    }

    @Test
    fun isValidConsecutive_validatesMaxConsecutiveNumbers() {
        assertTrue(SmartNumberGenerator.isValidConsecutive(listOf(10, 11, 12, 25, 30, 40), maxConsecutive = 3)) // 10,11,12 (3개 연속)
        assertEquals(false, SmartNumberGenerator.isValidConsecutive(listOf(10, 11, 12, 13, 30, 40), maxConsecutive = 3)) // 10,11,12,13 (4개 연속) -> false
        // 기본값(3) 검증
        assertTrue(SmartNumberGenerator.isValidConsecutive(listOf(10, 11, 12, 25, 30, 40)))
        assertEquals(false, SmartNumberGenerator.isValidConsecutive(listOf(10, 11, 12, 13, 30, 40)))
    }

    @Test
    fun generateNumbers_multipleRunsAlwaysSucceed() {
        for (i in 1..50) {
            val numbers = SmartNumberGenerator.generateNumbers(LottoStrategy.BALANCED)
            assertEquals(6, numbers.size)
            assertTrue(numbers.all { it in 1..45 })
        }
    }

    @Test
    fun generateNumbers_hotAndColdStrategiesSatisfyConstraints() {
        for (i in 1..30) {
            val hotNumbers = SmartNumberGenerator.generateNumbers(LottoStrategy.HOT_FOCUS)
            assertEquals(6, hotNumbers.size)
            assertTrue(SmartNumberGenerator.validateAllConstraints(hotNumbers, LottoStrategy.HOT_FOCUS))

            val coldNumbers = SmartNumberGenerator.generateNumbers(LottoStrategy.COLD_FOCUS)
            assertEquals(6, coldNumbers.size)
            assertTrue(SmartNumberGenerator.validateAllConstraints(coldNumbers, LottoStrategy.COLD_FOCUS))
        }
    }

    @Test
    fun helperMethods_calculateAccurately() {
        assertEquals(1, SmartNumberGenerator.getMaxConsecutive(listOf(1, 3, 5, 7, 9, 11)))
        assertEquals(2, SmartNumberGenerator.getMaxConsecutive(listOf(1, 2, 5, 7, 9, 11)))
        assertEquals(3, SmartNumberGenerator.getMaxConsecutive(listOf(1, 2, 3, 7, 9, 11)))

        assertEquals(1, SmartNumberGenerator.getMaxSameLastDigit(listOf(1, 2, 3, 4, 5, 6)))
        assertEquals(2, SmartNumberGenerator.getMaxSameLastDigit(listOf(1, 11, 3, 4, 5, 6)))
        assertEquals(3, SmartNumberGenerator.getMaxSameLastDigit(listOf(1, 11, 21, 4, 5, 6)))

        assertEquals(0, SmartNumberGenerator.getMissingDecadeCount(listOf(1, 10, 20, 30, 40, 45)))
        assertEquals(1, SmartNumberGenerator.getMissingDecadeCount(listOf(1, 2, 10, 20, 30, 35)))
        assertEquals(2, SmartNumberGenerator.getMissingDecadeCount(listOf(1, 2, 10, 11, 20, 25)))
        assertEquals(3, SmartNumberGenerator.getMissingDecadeCount(listOf(1, 2, 3, 10, 11, 12)))
    }

    @Test
    fun smartStrategies_minimumOneStrategyMaintained() {
        // 3개 선택 상태에서 1개 제거 -> 2개 남음
        var current = setOf(StrategyType.BALANCED, StrategyType.HOT_FOCUS, StrategyType.COLD_FOCUS)
        var next = if (StrategyType.BALANCED in current) {
            if (current.size > 1) current - StrategyType.BALANCED else current
        } else current + StrategyType.BALANCED
        assertEquals(2, next.size)

        // 1개만 남은 상태에서 마지막 전략 제거 시도 -> 제거되지 않고 1개 유지 (0개 방지)
        current = setOf(StrategyType.HOT_FOCUS)
        next = if (StrategyType.HOT_FOCUS in current) {
            if (current.size > 1) current - StrategyType.HOT_FOCUS else current
        } else current + StrategyType.HOT_FOCUS
        assertEquals(1, next.size)
        assertTrue(StrategyType.HOT_FOCUS in next)
    }

    @Test
    fun smartFilterSettings_applyToUpdatesStrategyConstraints() {
        val custom = SmartFilterSettings(
            minSum = 125,
            maxSum = 155,
            maxConsecutive = 2,
            maxSameLastDigit = 2,
        )
        val strategy = custom.applyTo(LottoStrategy.HOT_FOCUS)
        assertEquals(StrategyType.HOT_FOCUS, strategy.type)
        assertEquals(125, strategy.minSum)
        assertEquals(155, strategy.maxSum)
        assertEquals(2, strategy.maxConsecutive)
        assertEquals(2, strategy.maxSameLastDigit)

        val numbers = SmartNumberGenerator.generateNumbers(strategy, random = Random(123))
        assertTrue(SmartNumberGenerator.validateAllConstraints(numbers, strategy))
        assertTrue(numbers.sum() in 125..155)
        assertTrue(SmartNumberGenerator.getMaxConsecutive(numbers) <= 2)
        assertTrue(SmartNumberGenerator.getMaxSameLastDigit(numbers) <= 2)
    }
}
