package com.fajar.spamcallblocker.data

enum class RuleType(val displayName: String) {
    STARTS_WITH("Starts with"),
    CONTAINS("Contains"),
    ENDS_WITH("Ends with"),
    EXACT("Exact match")
}

data class BlockedCall(
    val id: Long,
    val phoneNumber: String,
    val timestamp: Long,
    val reason: String
)

data class WhitelistItem(
    val id: Long,
    val phoneNumber: String,
    val note: String,
    val createdAt: Long
)

data class BlockRule(
    val id: Long,
    val ruleType: RuleType,
    val pattern: String,
    val note: String,
    val createdAt: Long,
    val isActive: Boolean
)
