package com.umicorp.autolotto.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 구매 내역 로컬 영속 캐시.
 *
 * 네트워크가 느리거나 오프라인 상태에서도 최근 구매한 티켓과 내역을 즉시 표시할 수 있도록
 * 앱 내부 저장소(filesDir)에 JSON 형태로 사용자별 구매 내역을 캐싱한다.
 */
object PurchaseStore {

    private val ISO_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun getCacheFile(baseDir: File, userId: String?): File {
        val safeId = userId?.filter { it.isLetterOrDigit() }?.ifEmpty { "default" } ?: "default"
        return File(baseDir, "purchases_cache_$safeId.json")
    }

    /** 캐시된 구매 내역 목록 로드 */
    @Synchronized
    fun loadPurchases(context: Context, userId: String?): List<Purchase> =
        loadPurchases(context.filesDir, userId)

    @Synchronized
    fun loadPurchases(baseDir: File, userId: String?): List<Purchase> {
        val file = getCacheFile(baseDir, userId)
        if (!file.exists()) return emptyList()
        return try {
            val text = file.readText()
            val array = JSONArray(text)
            val list = mutableListOf<Purchase>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                parsePurchase(obj)?.let { list.add(it) }
            }
            list.sortedByDescending { it.round }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 전체 구매 내역 캐시 저장 */
    @Synchronized
    fun savePurchases(context: Context, userId: String?, purchases: List<Purchase>) =
        savePurchases(context.filesDir, userId, purchases)

    @Synchronized
    fun savePurchases(baseDir: File, userId: String?, purchases: List<Purchase>) {
        try {
            val file = getCacheFile(baseDir, userId)
            val array = JSONArray()
            purchases.forEach { p -> array.put(toJson(p)) }
            val tmp = File(file.parentFile ?: baseDir, "${file.name}.tmp")
            tmp.writeText(array.toString())
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        } catch (_: Exception) {
            // 캐시 저장 실패는 앱 동작을 막지 않는다
        }
    }

    /** 단일 구매 내역 병합 저장 (Worker 구매 완료 or 즉시 구매 직후 호출) */
    @Synchronized
    fun saveOrMergePurchase(context: Context, userId: String?, purchase: Purchase) =
        saveOrMergePurchase(context.filesDir, userId, purchase)

    @Synchronized
    fun saveOrMergePurchase(baseDir: File, userId: String?, purchase: Purchase) {
        val current = loadPurchases(baseDir, userId)
        val merged = mergePurchasesByRound(listOf(purchase) + current).sortedByDescending { it.round }
        savePurchases(baseDir, userId, merged)
    }

    /** 여러 구매 내역 병합 저장 (Worker 결과 확인 등) */
    @Synchronized
    fun saveOrMergePurchases(context: Context, userId: String?, purchases: List<Purchase>) =
        saveOrMergePurchases(context.filesDir, userId, purchases)

    @Synchronized
    fun saveOrMergePurchases(baseDir: File, userId: String?, purchases: List<Purchase>) {
        if (purchases.isEmpty()) return
        val current = loadPurchases(baseDir, userId)
        val merged = mergePurchasesByRound(purchases + current).sortedByDescending { it.round }
        savePurchases(baseDir, userId, merged)
    }

    private fun toJson(p: Purchase): JSONObject = JSONObject().apply {
        put("round", p.round)
        put("date", p.date.format(ISO_FMT))
        put("numbers", JSONArray().apply {
            p.numbers.forEach { nums ->
                put(JSONArray(nums))
            }
        })
        put("autoCount", p.autoCount)
        put("manualCount", p.manualCount)
        put("amount", p.amount)
        put("checked", p.checked)
        p.rank?.let { put("rank", it) }
        put("prize", p.prize)
        p.gameRanks?.let { ranks ->
            put("gameRanks", JSONArray(ranks))
        }
        p.gamePrizes?.let { prizes ->
            put("gamePrizes", JSONArray(prizes))
        }
        p.winningNumbers?.let { wins ->
            put("winningNumbers", JSONArray(wins))
        }
        p.bonusNumber?.let { put("bonusNumber", it) }
    }

    private fun parsePurchase(obj: JSONObject): Purchase? {
        val round = obj.optInt("round", 0)
        if (round <= 0) return null
        val dateStr = obj.optString("date")
        val date = runCatching { LocalDateTime.parse(dateStr, ISO_FMT) }.getOrElse { LocalDateTime.now() }

        val numsArr = obj.optJSONArray("numbers") ?: return null
        val numbers = mutableListOf<List<Int>>()
        for (i in 0 until numsArr.length()) {
            val gameArr = numsArr.optJSONArray(i) ?: continue
            val gameNums = mutableListOf<Int>()
            for (j in 0 until gameArr.length()) {
                gameNums.add(gameArr.optInt(j))
            }
            if (gameNums.size == 6) numbers.add(gameNums)
        }
        if (numbers.isEmpty()) return null

        val autoCount = obj.optInt("autoCount", 0)
        val manualCount = obj.optInt("manualCount", numbers.size - autoCount)
        val amount = obj.optInt("amount", numbers.size * 1000)
        val checked = obj.optBoolean("checked", false)
        val rank = if (obj.has("rank") && !obj.isNull("rank")) obj.optString("rank") else null
        val prize = obj.optLong("prize", 0L)

        val gameRanks = obj.optJSONArray("gameRanks")?.let { arr ->
            (0 until arr.length()).map { arr.optString(it) }
        }
        val gamePrizes = obj.optJSONArray("gamePrizes")?.let { arr ->
            (0 until arr.length()).map { arr.optLong(it) }
        }
        val winningNumbers = obj.optJSONArray("winningNumbers")?.let { arr ->
            (0 until arr.length()).map { arr.optInt(it) }
        }
        val bonusNumber = if (obj.has("bonusNumber") && !obj.isNull("bonusNumber")) obj.optInt("bonusNumber") else null

        return Purchase(
            round = round,
            date = date,
            numbers = numbers,
            autoCount = autoCount,
            manualCount = manualCount,
            amount = amount,
            checked = checked,
            rank = rank,
            prize = prize,
            gameRanks = gameRanks,
            gamePrizes = gamePrizes,
            winningNumbers = winningNumbers,
            bonusNumber = bonusNumber,
        )
    }
}
