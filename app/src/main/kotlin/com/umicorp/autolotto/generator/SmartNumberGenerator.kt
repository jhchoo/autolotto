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
 * 로또 분석 전략 및 6대 통계 필터링 기준
 *
 * - 홀짝 / 저고 비율: 3:3, 4:2, 2:4 (극단적 편중 배제)
 * - 전멸 구간: 1·10·20·30·40번대(5개 구간) 중 미출현 구간 1~2개
 * - 동일 끝수 / 연속 번호 / 총합 범위: 사용자 설정([SmartFilterSettings])으로 조정 가능
 */
data class LottoStrategy(
    val type: StrategyType = StrategyType.BALANCED,
    val hotCount: Int = 2,
    val coldCount: Int = 2,
    val randomCount: Int = 2,
    val oddEvenRatios: Set<Pair<Int, Int>> = setOf(3 to 3, 4 to 2, 2 to 4),
    val highLowRatios: Set<Pair<Int, Int>> = setOf(3 to 3, 4 to 2, 2 to 4),
    val missingDecadeCounts: Set<Int> = setOf(1, 2),
    val maxSameLastDigit: Int = 3,
    val maxConsecutive: Int = 3,
    val minSum: Int = 115,
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
 * 사용자가 설정한 스마트 번호 통계 필터 조건 (SecureStore 영속화 및 자동·즉시 구매 공용)
 *
 * @property minSum 번호 6개 총합 하한 (기본 115, 허용 110..180)
 * @property maxSum 번호 6개 총합 상한 (기본 175, 허용 110..180)
 * @property maxConsecutive 허용 최대 연속 번호 길이 (2 또는 3, 기본 3)
 * @property maxSameLastDigit 허용 최대 동일 끝수 개수 (2 또는 3, 기본 3)
 */
data class SmartFilterSettings(
    val minSum: Int = 115,
    val maxSum: Int = 175,
    val maxConsecutive: Int = 3,
    val maxSameLastDigit: Int = 3,
) {
    fun applyTo(strategy: LottoStrategy): LottoStrategy = strategy.copy(
        minSum = minSum,
        maxSum = maxSum,
        maxConsecutive = maxConsecutive,
        maxSameLastDigit = maxSameLastDigit,
    )

    companion object {
        val DEFAULT = SmartFilterSettings()
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
        val normalPool = analysis?.normalNumbers ?: NORMAL_NUMBERS

        repeat(maxAttempts) {
            val candidate = pickCandidateNumbers(strategy, hotPool, coldPool, normalPool, random)
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
        normalPool: List<Int>,
        random: Random,
    ): List<Int> {
        val selected = mutableSetOf<Int>()

        // 1. Hot 풀에서 hotCount만큼 추출
        val hShuffled = hotPool.shuffled(random)
        for (n in hShuffled) {
            if (selected.size >= strategy.hotCount) break
            selected.add(n)
        }

        // 2. Cold 풀에서 coldCount만큼 추출 (중복 제외)
        val cShuffled = coldPool.filter { it !in selected }.shuffled(random)
        for (n in cShuffled) {
            if (selected.size >= (strategy.hotCount + strategy.coldCount)) break
            selected.add(n)
        }

        // 3. Normal 풀에서 randomCount만큼 추출 (중복 제외)
        val nShuffled = normalPool.filter { it !in selected }.shuffled(random)
        for (n in nShuffled) {
            if (selected.size >= (strategy.hotCount + strategy.coldCount + strategy.randomCount)) break
            selected.add(n)
        }

        // 4. 풀이 부족할 경우 전체 번호에서 보충
        if (selected.size < 6) {
            val remaining = (1..45).filter { it !in selected }.shuffled(random)
            for (n in remaining) {
                if (selected.size >= 6) break
                selected.add(n)
            }
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
            isValidMissingDecades(sorted, strategy.missingDecadeCounts) &&
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

    /** 고저 비율 검증 (저: 1~22, 고: 23~45) */
    fun isValidHighLowRatio(
        numbers: List<Int>,
        allowedRatios: Set<Pair<Int, Int>> = setOf(3 to 3, 4 to 2, 2 to 4),
    ): Boolean {
        val lowCount = numbers.count { it in 1..22 }
        val highCount = numbers.size - lowCount
        return (lowCount to highCount) in allowedRatios
    }

    /** 전멸구간 개수 검증 (1~9, 10~19, 20~29, 30~39, 40~45 중 미출현 구간이 1~2개) */
    fun isValidMissingDecades(
        numbers: List<Int>,
        allowedCounts: Set<Int> = setOf(1, 2),
    ): Boolean {
        return getMissingDecadeCount(numbers) in allowedCounts
    }

    /** 끝자리 중복 검증 (동일 끝자리가 maxSame 이하) */
    fun isValidLastDigits(numbers: List<Int>, maxSame: Int = 3): Boolean {
        val lastDigitCounts = numbers.groupBy { it % 10 }
        return lastDigitCounts.values.all { it.size <= maxSame }
    }

    /** 연속 번호 검증 (최대 연속 개수가 maxConsecutive 이하) */
    fun isValidConsecutive(numbers: List<Int>, maxConsecutive: Int = 3): Boolean {
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

    /** 총합 범위 검증 (기본 115~175) */
    fun isValidSumRange(numbers: List<Int>, minSum: Int = 115, maxSum: Int = 175): Boolean {
        val sum = numbers.sum()
        return sum in minSum..maxSum
    }

    /** 최대 연속 번호 개수 계산 (예: [10, 11] -> 2, [1, 3, 5] -> 1) */
    fun getMaxConsecutive(numbers: List<Int>): Int {
        if (numbers.isEmpty()) return 0
        val sorted = numbers.sorted()
        var maxConsecutive = 1
        var currentConsecutive = 1
        for (i in 0 until sorted.size - 1) {
            if (sorted[i + 1] == sorted[i] + 1) {
                currentConsecutive++
                if (currentConsecutive > maxConsecutive) {
                    maxConsecutive = currentConsecutive
                }
            } else {
                currentConsecutive = 1
            }
        }
        return maxConsecutive
    }

    /** 동일 끝자리 최대 중복 개수 계산 (예: [7, 17] -> 2) */
    fun getMaxSameLastDigit(numbers: List<Int>): Int {
        if (numbers.isEmpty()) return 0
        val lastDigitCounts = numbers.groupBy { it % 10 }
        return lastDigitCounts.values.maxOfOrNull { it.size } ?: 1
    }

    /** 전멸구간(1번대/10번대/20번대/30번대/40번대 중 번호가 없는 구간) 개수 계산 */
    fun getMissingDecadeCount(numbers: List<Int>): Int {
        if (numbers.isEmpty()) return DECADE_RANGES.size
        return DECADE_RANGES.count { range -> numbers.none { it in range } }
    }

    private val DECADE_RANGES = listOf(1..9, 10..19, 20..29, 30..39, 40..45)
}
