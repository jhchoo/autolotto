package com.umicorp.autolotto.data

import android.content.Context
import java.io.File
import java.time.LocalDate
import java.util.Locale

/**
 * 당첨 번호 CSV 파일 헬퍼.
 *
 * `assets/winning_numbers.csv`를 읽어 로컬 SQLite DB 초기 시드(Seed) 데이터를 제공한다.
 * DB 일원화에 따라 앱 내부 저장소 CSV 복사/수정 로직은 제거되고,
 * 기존에 생성되었던 파일이 있다면 정리(delete)한다.
 */
object LottoHistoryCsvHelper {

    const val FILE_NAME = "winning_numbers.csv"

    /** 로또 1회차 추첨일 (2002-12-07) */
    private val FIRST_ROUND_DATE: LocalDate = LocalDate.of(2002, 12, 7)

    /** 앱 assets 폴더에서 winning_numbers.csv를 읽어 WinningResult 목록으로 파싱 */
    fun loadSeedResultsFromAssets(context: Context): List<WinningResult> {
        return runCatching {
            context.assets.open(FILE_NAME).bufferedReader(Charsets.UTF_8).use { reader ->
                parseCsv(reader.readText())
            }
        }.getOrDefault(emptyList())
    }

    /** 내부 저장소에 남아있는 레거시 CSV 파일 삭제 (DB 일원화 정리) */
    fun deleteInternalCsvIfExists(context: Context): Boolean {
        return runCatching {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        }.getOrDefault(false)
    }

    /** 앱 내부 저장소 winning_numbers.csv 파일 객체 (레거시/호환용) */
    fun getInternalCsvFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }

    /** CSV 파일이 존재하는지 확인 (레거시/호환용) */
    fun hasCsvFile(context: Context): Boolean {
        val internalFile = getInternalCsvFile(context)
        return internalFile.exists() && internalFile.length() > 0
    }

    /** 회차 번호로 추첨일 계산 (로또 6/45 1회: 2002-12-07, 매주 토요일) */
    fun calculateDrawDate(round: Int): String {
        return FIRST_ROUND_DATE.plusWeeks((round - 1).toLong()).toString()
    }

    /** 당첨번호 목록 -> CSV 문자열 변환 (최신순 내림차순 정렬) */
    fun toCsv(results: Collection<WinningResult>): String {
        val sb = StringBuilder()
        sb.append("회차,번호1,번호2,번호3,번호4,번호5,번호6,보너스,1등 당첨금,1등 당첨수\n")
        results.sortedByDescending { it.round }.forEach { r ->
            val nums = r.numbers.sorted().joinToString(",")
            val prizeStr = if (r.prize1st > 0) {
                "\"${String.format(Locale.KOREA, "%,d", r.prize1st)}\""
            } else {
                "0"
            }
            sb.append("${r.round},$nums,${r.bonus},$prizeStr,${r.winnerCount1st}\n")
        }
        return sb.toString()
    }

    /** CSV 한 줄을 따옴표를 고려하여 분리 */
    fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (char in line) {
            when {
                char == '\"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    tokens.add(sb.toString().trim())
                    sb.setLength(0)
                }
                else -> sb.append(char)
            }
        }
        tokens.add(sb.toString().trim())
        return tokens
    }

    /** CSV 문자열 -> WinningResult 목록 파싱 (다양한 CSV 포맷 대응) */
    fun parseCsv(csvText: String): List<WinningResult> {
        val results = mutableListOf<WinningResult>()
        val lines = csvText.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val tokens = parseCsvLine(trimmed)
            if (tokens.isEmpty()) continue

            val round = tokens[0].toIntOrNull() ?: continue // 헤더 행 스킵

            // 날짜 컬럼 찾기 (yyyy-MM-dd 등)
            val dateIndex = tokens.indexOfFirst { it.matches(Regex("\\d{4}[-./]\\d{2}[-./]\\d{2}")) }

            val date: String
            val numbers: List<Int>
            val bonus: Int
            val prize1st: Long
            val prize2nd: Long
            val prize3rd: Long
            val winnerCount1st: Int

            if (dateIndex != -1) {
                // 날짜 컬럼이 포함된 포맷 (예: round, date, num1~num6, bonus, prize1st~3rd)
                date = tokens[dateIndex]
                val numsAndPrizes = tokens.filterIndexed { index, _ ->
                    index != 0 && index != dateIndex
                }
                if (numsAndPrizes.size < 7) continue
                numbers = numsAndPrizes.take(6).mapNotNull { cleanNumber(it)?.toInt() }.sorted()
                if (numbers.size != 6) continue
                bonus = cleanNumber(numsAndPrizes[6])?.toInt() ?: 0
                prize1st = cleanNumber(numsAndPrizes.getOrNull(7)) ?: 0L
                prize2nd = cleanNumber(numsAndPrizes.getOrNull(8)) ?: 0L
                prize3rd = cleanNumber(numsAndPrizes.getOrNull(9)) ?: 0L
                winnerCount1st = 0
            } else {
                // 날짜 컬럼이 없는 포맷 (예: 회차, 번호1~번호6, 보너스, 1등 당첨금, 1등 당첨수)
                if (tokens.size < 8) continue
                date = calculateDrawDate(round)
                numbers = tokens.subList(1, 7).mapNotNull { cleanNumber(it)?.toInt() }.sorted()
                if (numbers.size != 6) continue
                bonus = cleanNumber(tokens.getOrNull(7))?.toInt() ?: 0
                prize1st = cleanNumber(tokens.getOrNull(8)) ?: 0L
                prize2nd = 0L
                prize3rd = 0L
                winnerCount1st = cleanNumber(tokens.getOrNull(9))?.toInt() ?: 0
            }

            results.add(
                WinningResult(
                    round = round,
                    numbers = numbers,
                    bonus = bonus,
                    date = date,
                    prize1st = prize1st,
                    prize2nd = prize2nd,
                    prize3rd = prize3rd,
                    winnerCount1st = winnerCount1st,
                ),
            )
        }
        return results.sortedBy { it.round }
    }

    private fun cleanNumber(str: String?): Long? {
        if (str == null) return null
        return str.replace("\"", "").replace(",", "").trim().toLongOrNull()
    }
}
