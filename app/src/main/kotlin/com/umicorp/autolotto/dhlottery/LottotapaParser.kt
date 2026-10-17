package com.umicorp.autolotto.dhlottery

import com.umicorp.autolotto.data.WinningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * lottotapa.com 전체 당첨번호 수집 및 정규식 파서
 */
object LottotapaParser {

    private const val LOTTOTAPA_URL_FMT = "https://lottotapa.com/stat/result_all.php?sel_start=1&sel_end=%d"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Regex 패턴: 1246회 로또 당첨번호 (2026-10-17) [1호기] 16 22 23 24 27 28 + 6
    private val RESULT_REGEX: Pattern = Pattern.compile(
        """(\d+)회\s*로또\s*당첨번호\s*\(([\d\-]+)\)(?:\s*\d+호기)?\s*(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s*\+\s*(\d+)""",
    )

    /**
     * Lottotapa 웹페이지에서 1회부터 [endRound]회까지 전체 당첨번호 수집
     */
    suspend fun fetchAndParse(endRound: Int): List<WinningResult> = withContext(Dispatchers.IO) {
        val url = String.format(java.util.Locale.US, LOTTOTAPA_URL_FMT, endRound)
        try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string().orEmpty()
                parseHtmlText(body)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** HTML / 텍스트 본문에서 태그 제거 후 정규식으로 당첨번호 추출 */
    fun parseHtmlText(text: String): List<WinningResult> {
        // HTML 태그 및 엔티티 제거 후 연속 공백 단일화
        val cleaned = text
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("&nbsp;?"), " ")
            .replace(Regex("&[a-zA-Z]+;"), " ")
            .replace(Regex("\\s+"), " ")

        val results = mutableListOf<WinningResult>()
        val matcher = RESULT_REGEX.matcher(cleaned)

        while (matcher.find()) {
            runCatching {
                val round = matcher.group(1)?.toInt() ?: return@runCatching
                val date = matcher.group(2).orEmpty()
                val n1 = matcher.group(3)?.toInt() ?: return@runCatching
                val n2 = matcher.group(4)?.toInt() ?: return@runCatching
                val n3 = matcher.group(5)?.toInt() ?: return@runCatching
                val n4 = matcher.group(6)?.toInt() ?: return@runCatching
                val n5 = matcher.group(7)?.toInt() ?: return@runCatching
                val n6 = matcher.group(8)?.toInt() ?: return@runCatching
                val bonus = matcher.group(9)?.toInt() ?: return@runCatching

                val numbers = listOf(n1, n2, n3, n4, n5, n6).sorted()
                if (numbers.size == 6 && numbers.all { it in 1..45 } && bonus in 1..45) {
                    results.add(
                        WinningResult(
                            round = round,
                            numbers = numbers,
                            bonus = bonus,
                            date = date,
                        ),
                    )
                }
            }
        }
        return results.sortedBy { it.round }
    }
}
