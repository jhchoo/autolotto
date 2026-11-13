package com.umicorp.autolotto.dhlottery

import com.umicorp.autolotto.data.WinningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * lottotapa.com 전체 당첨번호 수집 및 Jsoup 파서
 */
object LottotapaParser {

    private const val LOTTOTAPA_URL_FMT = "https://lottotapa.com/stat/result_all.php?sel_start=%d&sel_end=%d"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val ROUND_REGEX = Regex("(\\d+)회")
    private val DATE_REGEX = Regex("(\\d{4}[-./]\\d{2}[-./]\\d{2})")

    /**
     * Lottotapa 웹페이지에서 [startRound]회부터 [endRound]회까지 당첨번호 수집
     */
    suspend fun fetchAndParse(startRound: Int, endRound: Int): List<WinningResult> = withContext(Dispatchers.IO) {
        if (startRound > endRound) return@withContext emptyList()
        val url = String.format(java.util.Locale.US, LOTTOTAPA_URL_FMT, startRound, endRound)

        // 1. OkHttpClient로 가져오기 시도 -> Jsoup 파싱
        try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            val body = client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }
            if (body.isNotEmpty()) {
                val results = parseHtml(body)
                if (results.isNotEmpty()) return@withContext results
            }
        } catch (_: Exception) {}

        // 2. Jsoup.connect 직접 시도 (폴백)
        try {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(15_000)
                .get()
            val results = parseDocument(doc)
            if (results.isNotEmpty()) return@withContext results
        } catch (_: Exception) {}

        emptyList()
    }

    /** 1회부터 [endRound]회까지 수집 */
    suspend fun fetchAndParse(endRound: Int): List<WinningResult> = fetchAndParse(startRound = 1, endRound = endRound)

    /** HTML 문자열에서 당첨번호 파싱 */
    fun parseHtml(html: String): List<WinningResult> {
        val doc = Jsoup.parse(html)
        return parseDocument(doc)
    }

    /** Jsoup Document에서 h2 및 li 태그 기반으로 당첨번호 추출 */
    fun parseDocument(doc: org.jsoup.nodes.Document): List<WinningResult> {
        val results = mutableListOf<WinningResult>()

        // 1. 회차 정보가 담긴 h2 태그 선택 ("10회 로또 당첨번호 2003-02-08" 형태)
        val roundHeaders = doc.select("h2:contains(로또 당첨번호), h2:contains(로또당첨번호)")

        for (header in roundHeaders) {
            val title = header.text()
            val round = ROUND_REGEX.find(title)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val date = DATE_REGEX.find(title)?.groupValues?.get(1).orEmpty()

            // h2 태그 바로 다음에 나오는 번호 리스트(ul) 탐색
            var sibling = header.nextElementSibling()
            var liElements = sibling?.select("li")
            while (sibling != null && (liElements == null || liElements.isEmpty())) {
                sibling = sibling.nextElementSibling()
                liElements = sibling?.select("li")
            }

            val numbersText = liElements
                ?.map { it.text().trim() }
                ?.filter { it != "+" && it.isNotEmpty() }
                ?: emptyList()

            val numInts = numbersText.mapNotNull { it.toIntOrNull() }
            if (numInts.size >= 7) {
                val winningNumbers = numInts.take(6).sorted()
                val bonus = numInts[6]
                if (winningNumbers.all { it in 1..45 } && bonus in 1..45) {
                    results.add(
                        WinningResult(
                            round = round,
                            numbers = winningNumbers,
                            bonus = bonus,
                            date = date,
                        )
                    )
                }
            }
        }

        // h2 구조 파싱 결과가 없으면 정규식 폴백
        if (results.isEmpty()) {
            return parseHtmlTextFallback(doc.text())
        }

        return results.sortedBy { it.round }
    }

    /** 기존 텍스트 정규식 파서 (호환용) */
    fun parseHtmlText(text: String): List<WinningResult> {
        val fromHtml = parseHtml(text)
        if (fromHtml.isNotEmpty()) return fromHtml
        return parseHtmlTextFallback(text)
    }

    private val RESULT_REGEX: java.util.regex.Pattern = java.util.regex.Pattern.compile(
        """(\d+)회\s*로또\s*당첨번호\s*\(([\d\-]+)\)(?:\s*\d+호기)?\s*(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s*\+\s*(\d+)""",
    )

    private fun parseHtmlTextFallback(text: String): List<WinningResult> {
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
                        )
                    )
                }
            }
        }
        return results.sortedBy { it.round }
    }
}
