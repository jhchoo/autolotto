package com.umicorp.autolotto.dhlottery

import com.umicorp.autolotto.data.WinningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 당첨 확인 서비스 (Flutter ResultService 포트). 로그인 불필요.
 *
 * 원본은 자체 Dio(쿠키 비공유, followRedirects 기본 true)를 가졌다. 포트도 자체 세션을 기본으로 갖고,
 * 테스트는 MockWebServer 세션을 주입한다(Dart의 `ResultService([Dio? dio])` 선택 주입과 1:1).
 * 원본 Dio가 리다이렉트를 추적했으므로 두 GET은 `follow=true`.
 */
class ResultService(private val session: DhlotterySession = DhlotterySession()) {

    /**
     * 당첨번호 조회. [roundNo] null이면 최신 회차.
     * 실패(비200 / HTML / 빈 목록 / 예외) 시 null — 원본과 동일.
     */
    suspend fun getWinningNumbers(roundNo: Int? = null): WinningResult? = withContext(Dispatchers.IO) {
        try {
            // 쿠키 획득용 메인 방문
            session.get(session.baseUrl, follow = true).close()

            val targetRound = roundNo ?: (PurchaseService.getCurrentRound() - 1)
            var result = fetchRound(targetRound)
            if (result == null && roundNo == null && targetRound > 1) {
                // 직전 회차가 아직 미게시된 경우 그 이전 회차 폴백 시도
                result = fetchRound(targetRound - 1)
            }
            result
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchRound(targetRound: Int): WinningResult? {
        val url = session.base(ApiConstants.WINNING_NUMBER) + "?srchLtEpsd=$targetRound"
        return session.get(url, follow = true).use { resp ->
            if (resp.code != 200) return null
            val body = resp.body?.string().orEmpty()
            val items = JSONObject(body).optJSONObject("data")?.optJSONArray("list")
            if (items == null || items.length() == 0) return null

            val item = items.getJSONObject(0)
            val round = optInt(item, "ltEpsd")
            val numbers = listOf(
                optInt(item, "tm1WnNo"),
                optInt(item, "tm2WnNo"),
                optInt(item, "tm3WnNo"),
                optInt(item, "tm4WnNo"),
                optInt(item, "tm5WnNo"),
                optInt(item, "tm6WnNo"),
            ).filter { it in 1..45 }.sorted()
            if (numbers.size != 6 || round <= 0) return null

            val bonus = optInt(item, "bnsWnNo")
            val dateRaw = item.optString("ltRflYmd", "")
            val date = if (dateRaw.isNotBlank()) parseDate(dateRaw) else PurchaseService.getDrawDates(round).drawDate.toString()
            val prize1st = optLong(item, "rnk1WnAmt", 0L)
            val winnerCount1st = optInt(item, "rnk1WnNope", 0)
            val prize2nd = optLong(item, "rnk2WnAmt", 0L)
            val prize3rd = optLong(item, "rnk3WnAmt", 0L)

            WinningResult(
                round = round,
                numbers = numbers,
                bonus = bonus,
                date = date,
                prize1st = prize1st,
                prize2nd = prize2nd,
                prize3rd = prize3rd,
                winnerCount1st = winnerCount1st,
            )
        }
    }

    private fun optInt(item: JSONObject, key: String, default: Int = 0): Int {
        val v = item.opt(key) ?: return default
        return when (v) {
            is Number -> v.toInt()
            is String -> v.replace(",", "").trim().toIntOrNull() ?: default
            else -> default
        }
    }

    private fun optLong(item: JSONObject, key: String, default: Long = 0L): Long {
        val v = item.opt(key) ?: return default
        return when (v) {
            is Number -> v.toLong()
            is String -> v.replace(",", "").trim().toLongOrNull() ?: default
            else -> default
        }
    }

    companion object {
        /**
         * 내 번호 vs 당첨번호 매칭 (로컬 등수 계산).
         *
         * ⚠️ DESIGN: 앱은 표시에 이 값을 쓰지 않는다. 등수/당첨금의 출처는 dhlottery API(ticketDetail →
         * Purchase.gameRanks/gamePrizes)다. 원본 충실 포팅으로 남겨두되 결과 화면에 배선하지 말 것.
         */
        fun checkMatches(
            myNumbers: List<List<Int>>,
            winningNumbers: List<Int>,
            bonus: Int,
        ): List<MatchResult> {
            val winningSet = winningNumbers.toSet()
            val results = mutableListOf<MatchResult>()
            for (nums in myNumbers) {
                val numsSet = nums.toSet()
                val matched = numsSet.intersect(winningSet).toList().sorted()
                val count = matched.size
                val bonusMatch = numsSet.contains(bonus)

                val rank: String
                val prize: Int
                when {
                    count == 6 -> { rank = "rank1"; prize = 0 }            // 변동
                    count == 5 && bonusMatch -> { rank = "rank2"; prize = 0 } // 변동
                    count == 5 -> { rank = "rank3"; prize = 0 }            // 변동
                    count == 4 -> { rank = "rank4"; prize = 50000 }
                    count == 3 -> { rank = "rank5"; prize = 5000 }
                    else -> { rank = "nowin"; prize = 0 }
                }
                results.add(MatchResult(nums, matched, count, bonusMatch, rank, prize))
            }
            return results
        }

        /** yyyyMMdd(8자리) → yyyy-MM-dd, 그 외는 원문 그대로 (Dart _parseDate). */
        private fun parseDate(raw: String): String =
            if (raw.length == 8) "${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)}" else raw
    }
}

/** 매칭 결과 (Flutter MatchResult 포트). 표시 비사용 — checkMatches 참조. */
data class MatchResult(
    val numbers: List<Int>,
    val matched: List<Int>,
    val matchCount: Int,
    val bonusMatch: Boolean,
    val rank: String,
    val prize: Int,
) {
    val isWinner: Boolean get() = rank != "nowin"
}
