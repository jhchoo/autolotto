package com.umicorp.autolotto.generator

import com.umicorp.autolotto.data.WinningResult

/** 번호별 출현 빈도 통계 */
data class NumberFrequency(
    val number: Int,
    val count: Int,
    val lastDrawnRound: Int,
)

/** 회차 범위 및 동적 Hot/Cold/Normal 분석 결과 */
data class FrequencyAnalysis(
    val minRound: Int,
    val maxRound: Int,
    val totalRounds: Int,
    val frequencies: List<NumberFrequency>,
    val hotNumbers: List<Int>,
    val coldNumbers: List<Int>,
    val normalNumbers: List<Int>,
) {
    /** 최다 출현 번호 TOP N (번호, 출현 횟수) 반환 */
    fun topFrequentNumbers(limit: Int = 5): List<Pair<Int, Int>> {
        return frequencies.take(limit).map { it.number to it.count }
    }
}

/**
 * 당첨 내역 기반 실시간 출현 빈도 분석기
 */
object LottoFrequencyAnalyzer {

    fun analyze(history: Map<Int, WinningResult>): FrequencyAnalysis {
        if (history.isEmpty()) {
            return FrequencyAnalysis(
                minRound = 1000,
                maxRound = 1000,
                totalRounds = 0,
                frequencies = (1..45).map { NumberFrequency(it, 0, 0) },
                hotNumbers = SmartNumberGenerator.HOT_NUMBERS,
                coldNumbers = SmartNumberGenerator.COLD_NUMBERS,
                normalNumbers = SmartNumberGenerator.NORMAL_NUMBERS,
            )
        }

        val minRound = history.keys.minOrNull() ?: 1000
        val maxRound = history.keys.maxOrNull() ?: 1000
        val totalRounds = history.size

        val counts = IntArray(46) { 0 }
        val lastRounds = IntArray(46) { 0 }

        for ((round, result) in history) {
            for (num in result.numbers) {
                if (num in 1..45) {
                    counts[num]++
                    if (round > lastRounds[num]) {
                        lastRounds[num] = round
                    }
                }
            }
        }

        val freqList = (1..45).map { num ->
            NumberFrequency(
                number = num,
                count = counts[num],
                lastDrawnRound = lastRounds[num],
            )
        }

        val hotSorted = freqList.sortedWith(
            compareByDescending<NumberFrequency> { it.count }
                .thenByDescending { it.lastDrawnRound } // 출현 횟수 많은 순, 같으면 최근 출현 순
                .thenBy { it.number },
        )
        val hotNumbers = hotSorted.take(15).map { it.number }

        val coldSorted = freqList.sortedWith(
            compareBy<NumberFrequency> { it.count }
                .thenBy { it.lastDrawnRound } // 출현 횟수 적은 순, 같으면 오랫동안 나오지 않은 순
                .thenBy { it.number },
        )
        val coldNumbers = coldSorted.take(15).map { it.number }
        val normalNumbers = (1..45).filter { it !in hotNumbers && it !in coldNumbers }

        return FrequencyAnalysis(
            minRound = minRound,
            maxRound = maxRound,
            totalRounds = totalRounds,
            frequencies = hotSorted,
            hotNumbers = hotNumbers,
            coldNumbers = coldNumbers,
            normalNumbers = normalNumbers,
        )
    }

    /** 최근 N회차(기본 100회) 기준 출현 빈도 분석 */
    fun analyzeRecent(history: Map<Int, WinningResult>, recentCount: Int = 100): FrequencyAnalysis {
        if (history.isEmpty()) return analyze(history)
        val sortedKeys = history.keys.sortedDescending()
        val recentKeys = sortedKeys.take(recentCount).toSet()
        val recentHistory = history.filterKeys { it in recentKeys }
        return analyze(recentHistory)
    }
}
