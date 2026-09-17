package com.fajar.spamcallblocker.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "spam_call_blocker.db"
        private const val DATABASE_VERSION = 1

        // Table History
        private const val TABLE_HISTORY = "history"
        private const val COL_HIST_ID = "id"
        private const val COL_HIST_PHONE = "phone_number"
        private const val COL_HIST_TIMESTAMP = "timestamp"
        private const val COL_HIST_REASON = "reason"

        // Table Whitelist
        private const val TABLE_WHITELIST = "whitelist"
        private const val COL_WHITE_ID = "id"
        private const val COL_WHITE_PHONE = "phone_number"
        private const val COL_WHITE_NOTE = "note"
        private const val COL_WHITE_CREATED = "created_at"

        // Table Rules
        private const val TABLE_RULES = "block_rules"
        private const val COL_RULE_ID = "id"
        private const val COL_RULE_TYPE = "rule_type"
        private const val COL_RULE_PATTERN = "pattern"
        private const val COL_RULE_NOTE = "note"
        private const val COL_RULE_CREATED = "created_at"
        private const val COL_RULE_ACTIVE = "is_active"

        fun normalizeNumber(raw: String): String {
            return raw.replace(Regex("[^0-9+]"), "").trim()
        }

        fun stripToDigits(raw: String): String {
            return raw.replace(Regex("[^0-9]"), "")
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_HISTORY (
                $COL_HIST_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_HIST_PHONE TEXT NOT NULL,
                $COL_HIST_TIMESTAMP INTEGER NOT NULL,
                $COL_HIST_REASON TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_WHITELIST (
                $COL_WHITE_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_WHITE_PHONE TEXT NOT NULL UNIQUE,
                $COL_WHITE_NOTE TEXT,
                $COL_WHITE_CREATED INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_RULES (
                $COL_RULE_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_RULE_TYPE TEXT NOT NULL,
                $COL_RULE_PATTERN TEXT NOT NULL,
                $COL_RULE_NOTE TEXT,
                $COL_RULE_CREATED INTEGER NOT NULL,
                $COL_RULE_ACTIVE INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_WHITELIST")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_RULES")
        onCreate(db)
    }

    // --- History Operations ---
    fun insertHistory(phoneNumber: String, timestamp: Long, reason: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_HIST_PHONE, phoneNumber)
            put(COL_HIST_TIMESTAMP, timestamp)
            put(COL_HIST_REASON, reason)
        }
        return db.insert(TABLE_HISTORY, null, values)
    }

    fun getAllHistory(): List<BlockedCall> {
        val list = mutableListOf<BlockedCall>()
        val db = readableDatabase
        val cursor: Cursor = db.query(
            TABLE_HISTORY,
            null,
            null,
            null,
            null,
            null,
            "$COL_HIST_TIMESTAMP DESC"
        )
        cursor.use {
            val idCol = it.getColumnIndexOrThrow(COL_HIST_ID)
            val phoneCol = it.getColumnIndexOrThrow(COL_HIST_PHONE)
            val timeCol = it.getColumnIndexOrThrow(COL_HIST_TIMESTAMP)
            val reasonCol = it.getColumnIndexOrThrow(COL_HIST_REASON)
            while (it.moveToNext()) {
                list.add(
                    BlockedCall(
                        id = it.getLong(idCol),
                        phoneNumber = it.getString(phoneCol),
                        timestamp = it.getLong(timeCol),
                        reason = it.getString(reasonCol)
                    )
                )
            }
        }
        return list
    }

    fun deleteHistoryItem(id: Long): Int {
        val db = writableDatabase
        return db.delete(TABLE_HISTORY, "$COL_HIST_ID = ?", arrayOf(id.toString()))
    }

    fun clearHistory(): Int {
        val db = writableDatabase
        return db.delete(TABLE_HISTORY, null, null)
    }

    // --- Whitelist Operations ---
    fun insertWhitelist(phoneNumber: String, note: String = ""): Boolean {
        val db = writableDatabase
        val normalized = normalizeNumber(phoneNumber)
        if (normalized.isEmpty()) return false

        val values = ContentValues().apply {
            put(COL_WHITE_PHONE, normalized)
            put(COL_WHITE_NOTE, note)
            put(COL_WHITE_CREATED, System.currentTimeMillis())
        }
        val result = db.insertWithOnConflict(
            TABLE_WHITELIST,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        return result != -1L
    }

    fun getAllWhitelist(): List<WhitelistItem> {
        val list = mutableListOf<WhitelistItem>()
        val db = readableDatabase
        val cursor: Cursor = db.query(
            TABLE_WHITELIST,
            null,
            null,
            null,
            null,
            null,
            "$COL_WHITE_CREATED DESC"
        )
        cursor.use {
            val idCol = it.getColumnIndexOrThrow(COL_WHITE_ID)
            val phoneCol = it.getColumnIndexOrThrow(COL_WHITE_PHONE)
            val noteCol = it.getColumnIndexOrThrow(COL_WHITE_NOTE)
            val createdCol = it.getColumnIndexOrThrow(COL_WHITE_CREATED)
            while (it.moveToNext()) {
                list.add(
                    WhitelistItem(
                        id = it.getLong(idCol),
                        phoneNumber = it.getString(phoneCol),
                        note = it.getString(noteCol) ?: "",
                        createdAt = it.getLong(createdCol)
                    )
                )
            }
        }
        return list
    }

    fun deleteWhitelistItem(id: Long): Int {
        val db = writableDatabase
        return db.delete(TABLE_WHITELIST, "$COL_WHITE_ID = ?", arrayOf(id.toString()))
    }

    fun isWhitelisted(rawNumber: String): Boolean {
        val normalized = normalizeNumber(rawNumber)
        val digits = stripToDigits(rawNumber)
        if (digits.isEmpty()) return false

        val db = readableDatabase
        val cursor = db.query(
            TABLE_WHITELIST,
            arrayOf(COL_WHITE_PHONE),
            null,
            null,
            null,
            null,
            null
        )
        cursor.use {
            val phoneCol = it.getColumnIndexOrThrow(COL_WHITE_PHONE)
            while (it.moveToNext()) {
                val whitePhone = it.getString(phoneCol)
                val whiteDigits = stripToDigits(whitePhone)
                if (whitePhone == normalized || whiteDigits == digits) {
                    return true
                }
                // Check prefix variant (e.g. 081 vs 6281)
                if (digits.length >= 8 && whiteDigits.length >= 8) {
                    val tailDigits = digits.takeLast(8)
                    val whiteTail = whiteDigits.takeLast(8)
                    if (tailDigits == whiteTail && (digits.startsWith("0") || digits.startsWith("62")) &&
                        (whiteDigits.startsWith("0") || whiteDigits.startsWith("62"))
                    ) {
                        return true
                    }
                }
            }
        }
        return false
    }

    // --- Block Rules Operations ---
    fun insertBlockRule(type: RuleType, pattern: String, note: String = ""): Boolean {
        val cleanPattern = pattern.trim()
        if (cleanPattern.isEmpty()) return false

        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_RULE_TYPE, type.name)
            put(COL_RULE_PATTERN, cleanPattern)
            put(COL_RULE_NOTE, note)
            put(COL_RULE_CREATED, System.currentTimeMillis())
            put(COL_RULE_ACTIVE, 1)
        }
        val result = db.insert(TABLE_RULES, null, values)
        return result != -1L
    }

    fun getAllRules(): List<BlockRule> {
        val list = mutableListOf<BlockRule>()
        val db = readableDatabase
        val cursor: Cursor = db.query(
            TABLE_RULES,
            null,
            null,
            null,
            null,
            null,
            "$COL_RULE_CREATED DESC"
        )
        cursor.use {
            val idCol = it.getColumnIndexOrThrow(COL_RULE_ID)
            val typeCol = it.getColumnIndexOrThrow(COL_RULE_TYPE)
            val patternCol = it.getColumnIndexOrThrow(COL_RULE_PATTERN)
            val noteCol = it.getColumnIndexOrThrow(COL_RULE_NOTE)
            val createdCol = it.getColumnIndexOrThrow(COL_RULE_CREATED)
            val activeCol = it.getColumnIndexOrThrow(COL_RULE_ACTIVE)
            while (it.moveToNext()) {
                val typeName = it.getString(typeCol)
                val ruleType = try {
                    RuleType.valueOf(typeName)
                } catch (_: Exception) {
                    RuleType.CONTAINS
                }
                list.add(
                    BlockRule(
                        id = it.getLong(idCol),
                        ruleType = ruleType,
                        pattern = it.getString(patternCol),
                        note = it.getString(noteCol) ?: "",
                        createdAt = it.getLong(createdCol),
                        isActive = it.getInt(activeCol) == 1
                    )
                )
            }
        }
        return list
    }

    fun deleteBlockRule(id: Long): Int {
        val db = writableDatabase
        return db.delete(TABLE_RULES, "$COL_RULE_ID = ?", arrayOf(id.toString()))
    }

    fun removeRuleByNumberMatch(rawNumber: String): Int {
        val normalized = normalizeNumber(rawNumber)
        val digits = stripToDigits(rawNumber)
        val rules = getAllRules()
        var deletedCount = 0
        val db = writableDatabase

        for (rule in rules) {
            val rulePattern = rule.pattern.trim()
            val ruleDigits = stripToDigits(rulePattern)
            var matched = false
            when (rule.ruleType) {
                RuleType.EXACT -> {
                    if (normalized == rulePattern || digits == ruleDigits) matched = true
                }
                RuleType.STARTS_WITH -> {
                    if (digits.startsWith(ruleDigits)) matched = true
                }
                RuleType.CONTAINS -> {
                    if (digits.contains(ruleDigits)) matched = true
                }
                RuleType.ENDS_WITH -> {
                    if (digits.endsWith(ruleDigits)) matched = true
                }
            }
            if (matched) {
                db.delete(TABLE_RULES, "$COL_RULE_ID = ?", arrayOf(rule.id.toString()))
                deletedCount++
            }
        }
        return deletedCount
    }

    fun checkMatchingRule(rawNumber: String): Pair<Boolean, String?> {
        val normalized = normalizeNumber(rawNumber)
        val digits = stripToDigits(rawNumber)
        val rules = getAllRules().filter { it.isActive }

        for (rule in rules) {
            val pattern = rule.pattern.trim()
            val ruleDigits = stripToDigits(pattern)

            val matched = when (rule.ruleType) {
                RuleType.STARTS_WITH -> {
                    // Match normalized string or digits prefix
                    normalized.startsWith(pattern) ||
                            (ruleDigits.isNotEmpty() && digits.startsWith(ruleDigits)) ||
                            (pattern.startsWith("0") && normalized.startsWith("+62" + pattern.substring(1))) ||
                            (pattern.startsWith("+62") && normalized.startsWith("0" + pattern.substring(3)))
                }
                RuleType.CONTAINS -> {
                    normalized.contains(pattern) || (ruleDigits.isNotEmpty() && digits.contains(ruleDigits))
                }
                RuleType.ENDS_WITH -> {
                    normalized.endsWith(pattern) || (ruleDigits.isNotEmpty() && digits.endsWith(ruleDigits))
                }
                RuleType.EXACT -> {
                    normalized == pattern || (ruleDigits.isNotEmpty() && digits == ruleDigits)
                }
            }

            if (matched) {
                val label = "[${rule.ruleType.displayName}] $pattern" +
                        if (rule.note.isNotEmpty()) " (${rule.note})" else ""
                return Pair(true, label)
            }
        }
        return Pair(false, null)
    }
}
