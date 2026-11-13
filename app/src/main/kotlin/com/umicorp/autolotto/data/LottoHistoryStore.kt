package com.umicorp.autolotto.data

import android.content.Context
import com.umicorp.autolotto.dhlottery.ResultService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * 로또 당첨번호 영속 저장소 및 전역 메모리 캐시.
 *
 * 로컬 SQLite DB([LottoDatabase])를 단일 진실 공급원(SSOT)으로 사용하며,
 * UI 및 번호 추천 로직의 빠른 조회를 위해 전역 메모리([globalCache])를 관리한다.
 */
object LottoHistoryStore {

    /**
     * 앱 프로세스 전역 메모리 캐시 (회차 -> WinningResult)
     * 앱 시작 시 DB에서 로드하여 보관하며, 서비스 및 스마트번호추천에서 실시간 조회 시 사용된다.
     */
    @Volatile
    private var globalCache: Map<Int, WinningResult> = emptyMap()

    /** 전역 메모리에 보관된 당첨번호 맵 */
    val globalWinningResults: Map<Int, WinningResult>
        get() = globalCache

    /** 전역 메모리 캐시 직접 설정 (테스트 및 Mock용) */
    fun setGlobalWinningResults(results: Map<Int, WinningResult>) {
        globalCache = results
    }

    /**
     * DB가 비어있는 경우 assets/winning_numbers.csv를 읽어 DB에 초기 데이터를 주입(Seed)한다.
     * 또한 기존에 남아있던 내부 저장소의 불필요한 CSV 파일이 있다면 정리한다.
     */
    fun ensureDatabaseSeeded(context: Context) {
        val db = LottoDatabase(context)
        if (!db.hasData()) {
            val seedResults = LottoHistoryCsvHelper.loadSeedResultsFromAssets(context)
            if (seedResults.isNotEmpty()) {
                db.insertOrReplace(seedResults)
            }
        }
        // 레거시 내부 저장소 CSV 파일 정리
        LottoHistoryCsvHelper.deleteInternalCsvIfExists(context)

        // 메모리 캐시 최신화
        if (globalCache.isEmpty()) {
            globalCache = db.getAllResults()
        }
    }

    /**
     * 전체 당첨 결과 가져오기 (전역 메모리 우선 반환 -> 비어있으면 DB에서 로드 후 캐싱)
     * DB도 비어있으면 에셋 시드 데이터를 DB에 삽입 후 반환한다.
     */
    fun getAllWinningResults(context: Context? = null): Map<Int, WinningResult> {
        val currentCache = globalCache
        if (currentCache.isNotEmpty()) {
            return currentCache
        }
        if (context == null) return emptyMap()

        val db = LottoDatabase(context)
        if (!db.hasData()) {
            ensureDatabaseSeeded(context)
            return globalCache
        }

        val fromDb = db.getAllResults()
        if (fromDb.isNotEmpty()) {
            globalCache = fromDb
        }
        return fromDb
    }

    /** DB/캐시에 저장된 가장 최신 회차 반환 */
    fun getLatestStoredRound(context: Context? = null): Int? {
        val currentCache = globalCache
        if (currentCache.isNotEmpty()) {
            return currentCache.keys.maxOrNull()
        }
        if (context == null) return null
        val db = LottoDatabase(context)
        val latest = db.getLatestRound()
        if (latest != null) return latest

        val all = getAllWinningResults(context)
        return all.keys.maxOrNull()
    }

    /** 특정 회차부터 (예: 1200회) 최신순 당첨 결과 반환 */
    fun getResultsFromRound(context: Context? = null, startRound: Int): List<WinningResult> {
        val all = getAllWinningResults(context)
        return all.values
            .filter { it.round >= startRound }
            .sortedByDescending { it.round }
    }

    /** 지정된 회차 범위(startRound..endRound) 당첨 결과 반환 */
    fun getWinningResultsRange(context: Context? = null, startRound: Int, endRound: Int): List<WinningResult> {
        val all = getAllWinningResults(context)
        return all.values
            .filter { it.round in startRound..endRound }
            .sortedByDescending { it.round }
    }

    /** 필터링 및 페이징된 당첨 결과 반환 */
    fun getWinningResultsFiltered(
        context: Context? = null,
        searchQuery: String? = null,
        limit: Int = 10,
        offset: Int = 0,
    ): List<WinningResult> {
        val currentCache = globalCache
        if (currentCache.isNotEmpty()) {
            var seq = currentCache.values.asSequence()
            if (!searchQuery.isNullOrBlank()) {
                val q = searchQuery.trim()
                val qNum = q.toIntOrNull()
                seq = seq.filter { item ->
                    item.round.toString().contains(q) ||
                        item.date.contains(q) ||
                        (qNum != null && (item.numbers.contains(qNum) || item.bonus == qNum))
                }
            }
            return seq.sortedByDescending { it.round }
                .drop(offset)
                .take(limit)
                .toList()
        }
        if (context == null) return emptyList()
        val db = LottoDatabase(context)
        return db.getWinningResultsFiltered(searchQuery, limit, offset)
    }

    /** 저장된 전체 회차 개수 */
    fun getTotalCount(context: Context? = null): Int {
        val currentCache = globalCache
        if (currentCache.isNotEmpty()) {
            return currentCache.size
        }
        if (context == null) return 0
        val db = LottoDatabase(context)
        return db.getTotalCount()
    }

    /** 데이터 존재 여부 */
    fun hasData(context: Context): Boolean {
        if (globalCache.isNotEmpty()) return true
        val db = LottoDatabase(context)
        return db.hasData()
    }

    /**
     * 당첨 결과 일괄 저장 및 최신화.
     * 1) 로컬 DB(단일 진실 공급원)에 저장
     * 2) 전역 메모리 캐시 갱신
     */
    fun saveWinningResults(context: Context, results: List<WinningResult>) {
        if (results.isEmpty()) return

        // 1. DB에 저장
        val db = LottoDatabase(context)
        db.insertOrReplace(results)

        // 2. 전역 메모리 캐시 갱신
        val current = globalCache.toMutableMap()
        for (r in results) {
            current[r.round] = r
        }
        globalCache = current
    }

    /**
     * 초기 데이터 수집/로드:
     * 1. DB에 데이터가 없으면 assets/winning_numbers.csv에서 시드 주입
     * 2. 최신 회차까지 누락된 회차가 있으면 동행복권 API로 수집하여 DB에 저장
     */
    suspend fun ensureInitialHistoryLoaded(
        context: Context,
        resultService: ResultService,
        onProgress: ((Int, Int) -> Unit)? = null,
    ) = withContext(Dispatchers.IO) {
        if (globalCache.isEmpty()) {
            ensureDatabaseSeeded(context)
        }

        // 최신 회차까지 누락분이 있으면 동행복권 API로 동기화
        val latestStored = getLatestStoredRound(context) ?: 0
        val targetRound = com.umicorp.autolotto.dhlottery.PurchaseService.getCurrentRound() - 1
        if (latestStored < targetRound) {
            fetchAndSaveRange(context, resultService, latestStored + 1, targetRound, onProgress)
        }
    }

    /**
     * 회차 범위(startRound..endRound)를 서버에서 병렬로 수집하여 저장
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
