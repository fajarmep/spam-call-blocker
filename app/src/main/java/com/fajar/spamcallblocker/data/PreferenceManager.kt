package com.fajar.spamcallblocker.data

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("spam_call_blocker_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SERVICE_ENABLED = "key_service_enabled"
        private const val KEY_ALLOW_ALL_CONTACTS = "key_allow_all_contacts"
        private const val KEY_BLOCK_UNKNOWN_PRIVATE = "key_block_unknown_private"
        private const val KEY_BLOCK_ALL_CALLS = "key_block_all_calls"
        private const val KEY_SHOW_NOTIFICATION = "key_show_notification"
    }

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var isAllowAllContactsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_ALL_CONTACTS, true)
        set(value) = prefs.edit().putBoolean(KEY_ALLOW_ALL_CONTACTS, value).apply()

    var isBlockUnknownPrivateEnabled: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_UNKNOWN_PRIVATE, false)
        set(value) = prefs.edit().putBoolean(KEY_BLOCK_UNKNOWN_PRIVATE, value).apply()

    var isBlockAllCallsEnabled: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_ALL_CALLS, false)
        set(value) = prefs.edit().putBoolean(KEY_BLOCK_ALL_CALLS, value).apply()

    var isNotificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_SHOW_NOTIFICATION, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_NOTIFICATION, value).apply()
}
