package com.umicorp.autolotto.data

import android.content.Context
import com.umicorp.autolotto.dhlottery.ResultService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * 로또 당첨번호 영속 저장소 (LottoDatabase 기반)
 */
object LottoHistoryStore {

    /** 로컬 DB에서 전체 당첨 결과 가져오기 */
    fun getAllWinningResults(context: Context): Map<Int, WinningResult> {
        val db = LottoDatabase(context)
        return db.getAllResults()
    }

    /** 로컬 DB에 저장된 가장 최신 회차 반환 */
    fun getLatestStoredRound(context: Context): Int? {
        val db = LottoDatabase(context)
        return db.getLatestRound()
    }

    /** 특정 회차부터 (예: 1200회) 최신순 당첨 결과 반환 */
    fun getResultsFromRound(context: Context, startRound: Int): List<WinningResult> {
        val db = LottoDatabase(context)
        return db.getResultsFromRound(startRound)
    }

    /** 지정된 회차 범위(startRound..endRound) 당첨 결과 반환 */
    fun getWinningResultsRange(context: Context, startRound: Int, endRound: Int): List<WinningResult> {
        val db = LottoDatabase(context)
        return db.getWinningResultsRange(startRound, endRound)
    }

    /** 필터링 및 페이징된 당첨 결과 반환 */
    fun getWinningResultsFiltered(
        context: Context,
        searchQuery: String? = null,
        limit: Int = 10,
        offset: Int = 0,
    ): List<WinningResult> {
        val db = LottoDatabase(context)
        return db.getWinningResultsFiltered(searchQuery, limit, offset)
    }

    /** 저장된 전체 회차 개수 */
    fun getTotalCount(context: Context): Int {
        val db = LottoDatabase(context)
        return db.getTotalCount()
    }

    /** 데이터 존재 여부 */
    fun hasData(context: Context): Boolean {
        val db = LottoDatabase(context)
        return db.hasData()
    }

    /**
     * DB가 비어 있을 경우(최초 1회), Lottotapa에서 1회~최신회차 수집 시도 -> 실패 시 동행복권 API 폴백
     */
    suspend fun ensureInitialHistoryLoaded(
        context: Context,
        resultService: ResultService,
        onProgress: ((Int, Int) -> Unit)? = null,
    ) = withContext(Dispatchers.IO) {
        if (hasData(context)) return@withContext

        val endRound = com.umicorp.autolotto.dhlottery.PurchaseService.getCurrentRound()

        // 1. Lottotapa 수집 시도
        val lottotapaResults = com.umicorp.autolotto.dhlottery.LottotapaParser.fetchAndParse(endRound)
        if (lottotapaResults.isNotEmpty()) {
            saveWinningResults(context, lottotapaResults)
            return@withContext
        }

        // 2. Lottotapa 실패 시 동행복권 API로 1000회~endRound 폴백 수집
        fetchAndSaveRange(context, resultService, 1000, endRound, onProgress)
    }

    /** 당첨 결과 일괄 저장 */
    fun saveWinningResults(context: Context, results: List<WinningResult>) {
        val db = LottoDatabase(context)
        db.insertOrReplace(results)
    }

    /**
     * 회차 범위(startRound..endRound)를 서버에서 병렬로 수집하여 DB에 저장
     */
    suspend fun fetchAndSaveRange(
        context: Context,
        resultService: ResultService,
        startRound: Int,
        endRound: Int,
        onProgress: ((Int, Int) -> Unit)? = null,
    ) = withContext(Dispatchers.IO) {
        if (startRound > endRound) return@withContext

        val roundsToFetch = (startRound..endRound).toList()
        val total = roundsToFetch.size
        val fetchedResults = mutableListOf<WinningResult>()

        // 10개씩 배치 병렬 수행
        val chunkSize = 10
        var completed = 0

        for (chunk in roundsToFetch.chunked(chunkSize)) {
            val deferreds = chunk.map { roundNo ->
                async {
                    resultService.getWinningNumbers(roundNo)
                }
            }
            val resList = deferreds.awaitAll().filterNotNull()
            fetchedResults.addAll(resList)
            completed += chunk.size
            onProgress?.invoke(completed.coerceAtMost(total), total)
        }

        if (fetchedResults.isNotEmpty()) {
            saveWinningResults(context, fetchedResults)
        }
    }
}
