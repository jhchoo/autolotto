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
 * 앱 내부 저장소(winning_numbers.csv)와 로컬 SQLite DB,
 * 그리고 앱 전역 메모리([globalCache])를 단일 인터페이스로 통합 관리한다.
 */
object LottoHistoryStore {

    /**
     * 앱 프로세스 전역 메모리 캐시 (회차 -> WinningResult)
     * 앱 시작 시 내부 저장소 CSV 파일 또는 DB에서 로드하여 보관하며,
     * 서비스 및 스마트번호추천에서 실시간 조회 시 사용된다.
     */
    @Volatile
    private var globalCache: Map<Int, WinningResult> = emptyMap()

    /** 전역 메모리에 보관된 당첨번호 맵 */
    val globalWinningResults: Map<Int, WinningResult>
        get() = globalCache

    /** 전역 메모리 캐시 직접 설정 (테스트 및 외부 로더용) */
    fun setGlobalWinningResults(results: Map<Int, WinningResult>) {
        globalCache = results
    }

    /** 내부 저장소에 CSV 파일이 존재하는지 여부 */
    fun hasCsvFile(context: Context): Boolean {
        return LottoHistoryCsvHelper.hasCsvFile(context)
    }

    /** 내부 저장소 CSV 파일에서 당첨번호 읽기 */
    fun loadFromCsv(context: Context): List<WinningResult>? {
        return LottoHistoryCsvHelper.loadResults(context)
    }

    /** 내부 저장소 CSV 파일로 당첨번호 저장 */
    fun saveToCsv(context: Context, results: Collection<WinningResult>): Boolean {
        return LottoHistoryCsvHelper.saveResults(context, results)
    }

    /**
     * 전체 당첨 결과 가져오기 (전역 메모리 우선 반환 -> 비어있으면 DB에서 로드 후 캐싱)
     * 서비스 및 스마트번호추천 등에서 호출된다.
     */
    fun getAllWinningResults(context: Context? = null): Map<Int, WinningResult> {
        val currentCache = globalCache
        if (currentCache.isNotEmpty()) {
            return currentCache
        }
        if (context == null) return emptyMap()
        val db = LottoDatabase(context)
        val fromDb = db.getAllResults()
        if (fromDb.isNotEmpty()) {
            globalCache = fromDb
        }
        return fromDb
    }

    /** 로컬에 저장된 가장 최신 회차 반환 */
    fun getLatestStoredRound(context: Context? = null): Int? {
        val currentCache = globalCache
        if (currentCache.isNotEmpty()) {
            return currentCache.keys.maxOrNull()
        }
        if (context == null) return null
        val db = LottoDatabase(context)
        return db.getLatestRound()
    }

    /** 특정 회차부터 (예: 1200회) 최신순 당첨 결과 반환 */
    fun getResultsFromRound(context: Context? = null, startRound: Int): List<WinningResult> {
        val currentCache = globalCache
        if (currentCache.isNotEmpty()) {
            return currentCache.values
                .filter { it.round >= startRound }
                .sortedByDescending { it.round }
        }
        if (context == null) return emptyList()
        val db = LottoDatabase(context)
        return db.getResultsFromRound(startRound)
    }

    /** 지정된 회차 범위(startRound..endRound) 당첨 결과 반환 */
    fun getWinningResultsRange(context: Context? = null, startRound: Int, endRound: Int): List<WinningResult> {
        val currentCache = globalCache
        if (currentCache.isNotEmpty()) {
            return currentCache.values
                .filter { it.round in startRound..endRound }
                .sortedByDescending { it.round }
        }
        if (context == null) return emptyList()
        val db = LottoDatabase(context)
        return db.getWinningResultsRange(startRound, endRound)
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
        if (hasCsvFile(context)) return true
        val db = LottoDatabase(context)
        return db.hasData()
    }

    /**
     * 당첨 결과 일괄 저장.
     * 1) 로컬 DB 저장
     * 2) 전역 메모리 캐시 갱신
     * 3) 외부 저장소 CSV 파일로 저장
     */
    fun saveWinningResults(context: Context, results: List<WinningResult>) {
        if (results.isEmpty()) return

        // 1. DB에 저장
        val db = LottoDatabase(context)
        db.insertOrReplace(results)

        // 2. 전역 메모리 캐시 갱신
        val updated = globalCache.toMutableMap()
        for (r in results) {
            updated[r.round] = r
        }
        globalCache = updated

        // 3. 앱 내부 저장소 winning_numbers.csv 로 저장
        saveToCsv(context, updated.values)
    }

    /**
     * 초기 데이터 수집/로드:
     * 1. 앱 내부 저장소 CSV 파일(없으면 에셋에서 복사)을 읽어서 글로벌 변수와 DB에 로드
     * 2. 최신 회차까지 누락된 회차가 있으면 동행복권 API로 수집하여 내부 저장소 CSV에 갱신
     */
    suspend fun ensureInitialHistoryLoaded(
        context: Context,
        resultService: ResultService,
        onProgress: ((Int, Int) -> Unit)? = null,
    ) = withContext(Dispatchers.IO) {
        // 이미 글로벌 캐시에 데이터가 있으면 완료
        if (globalCache.isNotEmpty()) return@withContext

        // 내부 저장소(또는 에셋 복사) CSV 파일에서 로드
        val fromCsv = loadFromCsv(context)
        if (!fromCsv.isNullOrEmpty()) {
            saveWinningResults(context, fromCsv)
        } else {
            // DB에 데이터가 있는지 확인하여 로드
            val db = LottoDatabase(context)
            if (db.hasData()) {
                val fromDb = db.getAllResults()
                if (fromDb.isNotEmpty()) {
                    globalCache = fromDb
                    saveToCsv(context, fromDb.values)
                }
            }
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
