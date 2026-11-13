package com.umicorp.autolotto.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * 당첨 번호 외부 저장소 CSV 파일 입출력 헬퍼.
 *
 * 저장 위치: 외부저장소/Documents/AutoLotto/winning_numbers.csv
 * 형태: 쉼표 구분 CSV (round, date, num1~num6, bonus, prize1st~prize3rd)
 */
object LottoHistoryCsvHelper {

    const val DIR_NAME = "AutoLotto"
    const val FILE_NAME = "winning_numbers.csv"

    /** 공용 Documents/AutoLotto 디렉토리 */
    fun getPublicAutoLottoDir(): File {
        val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return File(documents, DIR_NAME)
    }

    /** 공용 Documents/AutoLotto/winning_numbers.csv 파일 객체 */
    fun getPublicCsvFile(): File {
        return File(getPublicAutoLottoDir(), FILE_NAME)
    }

    /** 앱 전용 외부 Documents 폴더 (권한 제한 환경 대비 Fallback) */
    fun getFallbackAutoLottoDir(context: Context): File {
        val extDocs = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        return File(extDocs, DIR_NAME)
    }

    fun getFallbackCsvFile(context: Context): File {
        return File(getFallbackAutoLottoDir(context), FILE_NAME)
    }

    /**
     * 외부 저장소 Documents/AutoLotto 에 CSV 파일이 존재하는지 확인
     */
    fun hasCsvFile(context: Context): Boolean {
        // 1. 공용 외부 저장소 File API 확인
        runCatching {
            val pubFile = getPublicCsvFile()
            if (pubFile.exists() && pubFile.length() > 0) return true
            val pubDir = getPublicAutoLottoDir()
            if (pubDir.exists() && pubDir.isDirectory) {
                val csvs = pubDir.listFiles { f -> f.isFile && f.extension.equals("csv", ignoreCase = true) && f.length() > 0 }
                if (!csvs.isNullOrEmpty()) return true
            }
        }

        // 2. MediaStore 조회 (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val resolver = context.contentResolver
                val collectionUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                val projection = arrayOf(MediaStore.MediaColumns._ID)
                val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf("Documents/$DIR_NAME%", "%.csv")
                resolver.query(collectionUri, projection, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) return true
                }
            }
        }

        // 3. Fallback 파일 확인
        runCatching {
            val fbFile = getFallbackCsvFile(context)
            if (fbFile.exists() && fbFile.length() > 0) return true
        }

        return false
    }

    /**
     * 외부 저장소에서 CSV 파일을 찾아 읽고 WinningResult 목록으로 파싱
     */
    fun loadResults(context: Context): List<WinningResult>? {
        // 1. 공용 외부 저장소 File API 직접 읽기
        runCatching {
            val pubFile = getPublicCsvFile()
            if (pubFile.exists() && pubFile.length() > 0) {
                val parsed = parseCsv(pubFile.readText(Charsets.UTF_8))
                if (parsed.isNotEmpty()) return parsed
            }
            val pubDir = getPublicAutoLottoDir()
            if (pubDir.exists() && pubDir.isDirectory) {
                val csvs = pubDir.listFiles { f -> f.isFile && f.extension.equals("csv", ignoreCase = true) && f.length() > 0 }
                val target = csvs?.firstOrNull { it.name == FILE_NAME } ?: csvs?.firstOrNull()
                if (target != null) {
                    val parsed = parseCsv(target.readText(Charsets.UTF_8))
                    if (parsed.isNotEmpty()) return parsed
                }
            }
        }

        // 2. MediaStore 조회 읽기 (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val resolver = context.contentResolver
                val collectionUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
                val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf("Documents/$DIR_NAME%", "%.csv")
                resolver.query(collectionUri, projection, selection, selectionArgs, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val uri = ContentUris.withAppendedId(collectionUri, id)
                        resolver.openInputStream(uri)?.use { stream ->
                            val text = stream.bufferedReader(Charsets.UTF_8).readText()
                            val parsed = parseCsv(text)
                            if (parsed.isNotEmpty()) return parsed
                        }
                    }
                }
            }
        }

        // 3. Fallback 파일 읽기
        runCatching {
            val fbFile = getFallbackCsvFile(context)
            if (fbFile.exists() && fbFile.length() > 0) {
                val parsed = parseCsv(fbFile.readText(Charsets.UTF_8))
                if (parsed.isNotEmpty()) return parsed
            }
        }

        return null
    }

    /**
     * 당첨번호 목록을 CSV 문자열로 변환하여 외부 저장소 Documents/AutoLotto/winning_numbers.csv 에 저장
     */
    fun saveResults(context: Context, results: Collection<WinningResult>): Boolean {
        if (results.isEmpty()) return false
        val csvText = toCsv(results)

        var saved = false

        // 1. 공용 외부 저장소 File API 시도
        runCatching {
            val pubDir = getPublicAutoLottoDir()
            if (!pubDir.exists()) pubDir.mkdirs()
            val pubFile = File(pubDir, FILE_NAME)
            pubFile.writeText(csvText, Charsets.UTF_8)
            if (pubFile.exists() && pubFile.length() > 0) {
                saved = true
            }
        }

        // 2. MediaStore API 시도 (Android 10+)
        if (!saved && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val resolver = context.contentResolver
                val collectionUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

                // 기존 파일이 있는지 확인
                val projection = arrayOf(MediaStore.MediaColumns._ID)
                val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf("Documents/$DIR_NAME%", FILE_NAME)

                var targetUri = resolver.query(collectionUri, projection, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        ContentUris.withAppendedId(collectionUri, id)
                    } else null
                }

                if (targetUri == null) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/$DIR_NAME")
                    }
                    targetUri = resolver.insert(collectionUri, values)
                }

                if (targetUri != null) {
                    resolver.openOutputStream(targetUri, "wt")?.use { os ->
                        os.write(csvText.toByteArray(Charsets.UTF_8))
                        os.flush()
                        saved = true
                    }
                }
            }
        }

        // 3. Fallback 파일 저장
        runCatching {
            val fbDir = getFallbackAutoLottoDir(context)
            if (!fbDir.exists()) fbDir.mkdirs()
            val fbFile = File(fbDir, FILE_NAME)
            fbFile.writeText(csvText, Charsets.UTF_8)
            if (!saved && fbFile.exists() && fbFile.length() > 0) {
                saved = true
            }
        }

        return saved
    }

    /**
     * 당첨번호 목록 -> CSV 문자열 변환
     */
    fun toCsv(results: Collection<WinningResult>): String {
        val sb = StringBuilder()
        sb.append("round,date,num1,num2,num3,num4,num5,num6,bonus,prize1st,prize2nd,prize3rd\n")
        results.sortedBy { it.round }.forEach { r ->
            val nums = r.numbers.sorted().joinToString(",")
            sb.append("${r.round},${r.date},$nums,${r.bonus},${r.prize1st},${r.prize2nd},${r.prize3rd}\n")
        }
        return sb.toString()
    }

    /**
     * CSV 문자열 -> WinningResult 목록 파싱 (유연하고 견고한 파싱)
     */
    fun parseCsv(csvText: String): List<WinningResult> {
        val results = mutableListOf<WinningResult>()
        val lines = csvText.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split(",").map { it.trim() }
            if (parts.size < 8) continue // 최소 회차, 날짜, 6개 번호 필요

            val round = parts[0].toIntOrNull() ?: continue // 헤더 행 스킵

            // 날짜 컬럼 찾기 (보통 인덱스 1)
            val dateIndex = parts.indexOfFirst { it.matches(Regex("\\d{4}[-./]\\d{2}[-./]\\d{2}")) }
            val date = if (dateIndex != -1) parts[dateIndex] else ""

            // 날짜를 제외한 나머지 숫자들 추출
            val numbersAndPrizes = parts.filterIndexed { index, _ ->
                index != 0 && index != dateIndex
            }.mapNotNull { it.toLongOrNull() }

            if (numbersAndPrizes.size < 7) continue // 6개 번호 + 1개 보너스 필요

            val numbers = numbersAndPrizes.take(6).map { it.toInt() }.sorted()
            val bonus = numbersAndPrizes[6].toInt()
            val prize1st = numbersAndPrizes.getOrNull(7) ?: 0L
            val prize2nd = numbersAndPrizes.getOrNull(8) ?: 0L
            val prize3rd = numbersAndPrizes.getOrNull(9) ?: 0L

            results.add(
                WinningResult(
                    round = round,
                    numbers = numbers,
                    bonus = bonus,
                    date = date,
                    prize1st = prize1st,
                    prize2nd = prize2nd,
                    prize3rd = prize3rd,
                )
            )
        }
        return results.sortedBy { it.round }
    }
}
