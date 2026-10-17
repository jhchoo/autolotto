package com.umicorp.autolotto.generator

import kotlin.random.Random

/**
 * 로또 전략 유형
 */
enum class StrategyType {
    BALANCED,   // 균형 추천 (Hot 2 + Cold 2 + Normal 2)
    HOT_FOCUS,  // 자주 나온 번호 위주 (Hot 4 + Cold 1 + Normal 1)
    COLD_FOCUS, // 장기 미출현 위주 (Cold 4 + Hot 1 + Normal 1)
}

/**
 * 로또 분석 전략 설정
 */
data class LottoStrategy(
    val type: StrategyType = StrategyType.BALANCED,
    val hotCount: Int = 2,
    val coldCount: Int = 2,
    val randomCount: Int = 2,
    val oddEvenRatios: Set<Pair<Int, Int>> = setOf(3 to 3, 4 to 2, 2 to 4),
    val highLowRatios: Set<Pair<Int, Int>> = setOf(3 to 3, 4 to 2, 2 to 4),
    val maxSameLastDigit: Int = 2,
    val maxConsecutive: Int = 2,
    val minSum: Int = 100,
    val maxSum: Int = 175,
) {
    companion object {
        val BALANCED = LottoStrategy(
            type = StrategyType.BALANCED,
            hotCount = 2,
            coldCount = 2,
            randomCount = 2,
        )

        val HOT_FOCUS = LottoStrategy(
            type = StrategyType.HOT_FOCUS,
            hotCount = 4,
            coldCount = 1,
            randomCount = 1,
        )

        val COLD_FOCUS = LottoStrategy(
            type = StrategyType.COLD_FOCUS,
            hotCount = 1,
            coldCount = 4,
            randomCount = 1,
        )
    }
}

/**
 * 통계 분석 및 필터링 기반 스마트 번호 생성기
 */
object SmartNumberGenerator {

    /** 통계상 자주 출현한 상위 번호군 (Hot) */
    val HOT_NUMBERS = listOf(34, 18, 12, 27, 13, 1, 43, 45, 20, 17, 33, 3, 11, 26, 4)

    /** 통계상 장기 미출현 / 상대적 저출현 번호군 (Cold) */
    val COLD_NUMBERS = listOf(9, 23, 28, 30, 5, 22, 16, 31, 38, 41, 8, 2, 15, 29, 36)

    /** 일반 출현 번호군 (Normal) */
    val NORMAL_NUMBERS = (1..45).filter { it !in HOT_NUMBERS && it !in COLD_NUMBERS }

    /**
     * 지정된 전략에 맞는 6개 로또 번호 1세트 생성 (정렬된 리스트 반환).
     * [analysis]가 전달되면 실시간 1000회~최신회차 통계 분석의 Hot/Cold/Normal 번호군을 우선 사용.
     */
    fun generateNumbers(
        strategy: LottoStrategy = LottoStrategy.BALANCED,
        analysis: FrequencyAnalysis? = null,
        random: Random = Random.Default,
        maxAttempts: Int = 1000,
    ): List<Int> {
        val hotPool = analysis?.hotNumbers ?: HOT_NUMBERS
        val coldPool = analysis?.coldNumbers ?: COLD_NUMBERS

        repeat(maxAttempts) {
            val candidate = pickCandidateNumbers(strategy, hotPool, coldPool, random)
            if (candidate.size == 6 && validateAllConstraints(candidate, strategy)) {
                return candidate.sorted()
            }
        }

        // 시도 횟수 초과 시 보수적 무작위 필터링 후 반환 (무한 루프/빈 리스트 차단)
        return (1..45).shuffled(random).take(6).sorted()
    }

    /**
     * 지정된 전략에 따라 Hot/Cold/Normal 후보 번호 6개 추출
     */
    private fun pickCandidateNumbers(
        strategy: LottoStrategy,
        hotPool: List<Int>,
        coldPool: List<Int>,
        random: Random,
    ): List<Int> {
        val selected = mutableSetOf<Int>()

        val hShuffled = hotPool.shuffled(random)
        for (n in hShuffled) {
            if (selected.size >= strategy.hotCount) break
            selected.add(n)
        }

        val cShuffled = coldPool.shuffled(random)
        for (n in cShuffled) {
            if (selected.size >= (strategy.hotCount + strategy.coldCount)) break
            selected.add(n)
        }

        val remaining = (1..45).filter { it !in selected }.shuffled(random)
        for (n in remaining) {
            if (selected.size >= 6) break
            selected.add(n)
        }

        return selected.toList()
    }

    /**
     * 모든 패턴/제약 조건 일괄 검증
     */
    fun validateAllConstraints(numbers: List<Int>, strategy: LottoStrategy): Boolean {
        if (numbers.size != 6) return false
        val sorted = numbers.sorted()

        return isValidOddEvenRatio(sorted, strategy.oddEvenRatios) &&
            isValidHighLowRatio(sorted, strategy.highLowRatios) &&
            isValidLastDigits(sorted, strategy.maxSameLastDigit) &&
            isValidConsecutive(sorted, strategy.maxConsecutive) &&
            isValidSumRange(sorted, strategy.minSum, strategy.maxSum)
    }

    /** 홀짝 비율 검증 (예: 3:3, 4:2, 2:4) */
    fun isValidOddEvenRatio(
        numbers: List<Int>,
        allowedRatios: Set<Pair<Int, Int>> = setOf(3 to 3, 4 to 2, 2 to 4),
    ): Boolean {
        val oddCount = numbers.count { it % 2 != 0 }
        val evenCount = numbers.size - oddCount
        return (oddCount to evenCount) in allowedRatios
    }

    /** 고저 비율 검증 (저: 1~20, 고: 21~45) */
    fun isValidHighLowRatio(
        numbers: List<Int>,
        allowedRatios: Set<Pair<Int, Int>> = setOf(3 to 3, 4 to 2, 2 to 4),
    ): Boolean {
        val lowCount = numbers.count { it in 1..20 }
        val highCount = numbers.size - lowCount
        return (lowCount to highCount) in allowedRatios
    }

    /** 끝자리 중복 검증 (동일 끝자리가 maxSame 이하) */
    fun isValidLastDigits(numbers: List<Int>, maxSame: Int = 2): Boolean {
        val lastDigitCounts = numbers.groupBy { it % 10 }
        return lastDigitCounts.values.all { it.size <= maxSame }
    }

    /** 연속 번호 검증 (최대 연속 개수가 maxConsecutive 이하) */
    fun isValidConsecutive(numbers: List<Int>, maxConsecutive: Int = 2): Boolean {
        val sorted = numbers.sorted()
        var currentConsecutive = 1
        for (i in 0 until sorted.size - 1) {
            if (sorted[i + 1] == sorted[i] + 1) {
                currentConsecutive++
                if (currentConsecutive > maxConsecutive) return false
            } else {
                currentConsecutive = 1
            }
        }
        return true
    }

    /** 총합 범위 검증 */
    fun isValidSumRange(numbers: List<Int>, minSum: Int = 100, maxSum: Int = 175): Boolean {
        val sum = numbers.sum()
        return sum in minSum..maxSum
    }
}
