package com.umicorp.autolotto.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 로또 당첨번호 저장을 위한 SQLite Light DB
 */
class LottoDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_WINNING (
                round INTEGER PRIMARY KEY,
                n1 INTEGER NOT NULL,
                n2 INTEGER NOT NULL,
                n3 INTEGER NOT NULL,
                n4 INTEGER NOT NULL,
                n5 INTEGER NOT NULL,
                n6 INTEGER NOT NULL,
                bonus INTEGER NOT NULL,
                date TEXT NOT NULL,
                prize1st LONG NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_WINNING")
        onCreate(db)
    }

    /** 당첨 결과 목록 일괄 삽입/갱신 */
    fun insertOrReplace(results: List<WinningResult>) {
        if (results.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (r in results) {
                if (r.numbers.size != 6) continue
                val values = ContentValues().apply {
                    put("round", r.round)
                    put("n1", r.numbers[0])
                    put("n2", r.numbers[1])
                    put("n3", r.numbers[2])
                    put("n4", r.numbers[3])
                    put("n5", r.numbers[4])
                    put("n6", r.numbers[5])
                    put("bonus", r.bonus)
                    put("date", r.date)
                    put("prize1st", r.prize1st)
                }
                db.insertWithOnConflict(TABLE_WINNING, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 저장된 전체 당첨 결과 반환 (회차 -> WinningResult) */
    fun getAllResults(): Map<Int, WinningResult> {
        val map = mutableMapOf<Int, WinningResult>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_WINNING,
            arrayOf("round", "n1", "n2", "n3", "n4", "n5", "n6", "bonus", "date", "prize1st"),
            null, null, null, null, "round ASC",
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val round = c.getInt(0)
                val nums = listOf(c.getInt(1), c.getInt(2), c.getInt(3), c.getInt(4), c.getInt(5), c.getInt(6))
                val bonus = c.getInt(7)
                val date = c.getString(8)
                val prize1st = c.getLong(9)
                map[round] = WinningResult(
                    round = round,
                    numbers = nums,
                    bonus = bonus,
                    date = date,
                    prize1st = prize1st,
                )
            }
        }
        return map
    }

    /** 특정 회차부터(예: 1200회 이상) 내림차순 리스트 반환 (설정 화면용) */
    fun getResultsFromRound(startRound: Int): List<WinningResult> {
        val list = mutableListOf<WinningResult>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_WINNING,
            arrayOf("round", "n1", "n2", "n3", "n4", "n5", "n6", "bonus", "date", "prize1st"),
            "round >= ?",
            arrayOf(startRound.toString()),
            null, null, "round DESC",
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val round = c.getInt(0)
                val nums = listOf(c.getInt(1), c.getInt(2), c.getInt(3), c.getInt(4), c.getInt(5), c.getInt(6))
                val bonus = c.getInt(7)
                val date = c.getString(8)
                val prize1st = c.getLong(9)
                list.add(
                    WinningResult(
                        round = round,
                        numbers = nums,
                        bonus = bonus,
                        date = date,
                        prize1st = prize1st,
                    ),
                )
            }
        }
        return list
    }

    /** 지정된 범위(startRound..endRound)의 당첨 결과 내림차순 리스트 반환 (최대 100건 보정) */
    fun getWinningResultsRange(startRound: Int, endRound: Int): List<WinningResult> {
        val list = mutableListOf<WinningResult>()
        val db = readableDatabase
        val minR = minOf(startRound, endRound)
        var maxR = maxOf(startRound, endRound)

        if (maxR - minR + 1 > 100) {
            maxR = minR + 99
        }

        val cursor = db.query(
            TABLE_WINNING,
            arrayOf("round", "n1", "n2", "n3", "n4", "n5", "n6", "bonus", "date", "prize1st"),
            "round >= ? AND round <= ?",
            arrayOf(minR.toString(), maxR.toString()),
            null, null, "round DESC",
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val round = c.getInt(0)
                val nums = listOf(c.getInt(1), c.getInt(2), c.getInt(3), c.getInt(4), c.getInt(5), c.getInt(6))
                val bonus = c.getInt(7)
                val date = c.getString(8)
                val prize1st = c.getLong(9)
                list.add(
                    WinningResult(
                        round = round,
                        numbers = nums,
                        bonus = bonus,
                        date = date,
                        prize1st = prize1st,
                    ),
                )
            }
        }
        return list
    }

    /** 검색어 및 페이징을 지원하는 당첨 결과 내림차순 리스트 반환 */
    fun getWinningResultsFiltered(
        searchQuery: String? = null,
        limit: Int = 10,
        offset: Int = 0,
    ): List<WinningResult> {
        val list = mutableListOf<WinningResult>()
        val db = readableDatabase

        val searchNum = searchQuery?.trim()?.toIntOrNull()
        val selection = if (searchNum != null) "round = ?" else null
        val selectionArgs = if (searchNum != null) arrayOf(searchNum.toString()) else null
        val limitClause = if (searchNum != null) null else "$offset, $limit"

        val cursor = db.query(
            TABLE_WINNING,
            arrayOf("round", "n1", "n2", "n3", "n4", "n5", "n6", "bonus", "date", "prize1st"),
            selection,
            selectionArgs,
            null, null, "round DESC",
            limitClause,
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val round = c.getInt(0)
                val nums = listOf(c.getInt(1), c.getInt(2), c.getInt(3), c.getInt(4), c.getInt(5), c.getInt(6))
                val bonus = c.getInt(7)
                val date = c.getString(8)
                val prize1st = c.getLong(9)
                list.add(
                    WinningResult(
                        round = round,
                        numbers = nums,
                        bonus = bonus,
                        date = date,
                        prize1st = prize1st,
                    ),
                )
            }
        }
        return list
    }

    /** DB에 저장된 전체 회차 개수 */
    fun getTotalCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_WINNING", null)
        cursor.use {
            if (it.moveToFirst()) return it.getInt(0)
        }
        return 0
    }

    /** DB에 저장된 데이터 존재 여부 */
    fun hasData(): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT 1 FROM $TABLE_WINNING LIMIT 1", null)
        cursor.use { return it.moveToFirst() }
    }

    /** DB에 저장된 가장 최신 회차 반환 */
    fun getLatestRound(): Int? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT MAX(round) FROM $TABLE_WINNING", null)
        cursor.use {
            if (it.moveToFirst() && !it.isNull(0)) {
                return it.getInt(0)
            }
        }
        return null
    }

    companion object {
        private const val DB_NAME = "lotto_history.db"
        private const val DB_VERSION = 1
        private const val TABLE_WINNING = "winning_numbers"
    }
}
