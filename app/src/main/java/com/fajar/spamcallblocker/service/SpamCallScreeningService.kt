package com.fajar.spamcallblocker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.core.app.NotificationCompat
import com.fajar.spamcallblocker.MainActivity
import com.fajar.spamcallblocker.R
import com.fajar.spamcallblocker.data.DatabaseHelper
import com.fajar.spamcallblocker.data.PreferenceManager
import com.fajar.spamcallblocker.receiver.CallNotificationReceiver

class SpamCallScreeningService : CallScreeningService() {

    companion object {
        private const val CHANNEL_ID = "blocked_calls_channel"
        private const val CHANNEL_NAME = "Panggilan Diblokir"
    }

    override fun onScreenCall(callDetails: Call.Details) {
        // Only evaluate incoming calls
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            allowCall(callDetails)
            return
        }

        val prefs = PreferenceManager(applicationContext)
        val dbHelper = DatabaseHelper(applicationContext)

        // Master switch
        if (!prefs.isServiceEnabled) {
            allowCall(callDetails)
            return
        }

        val rawNumber = callDetails.handle?.schemeSpecificPart?.trim() ?: ""

        // Case 1: Unknown / Hidden / Private number
        if (rawNumber.isEmpty() || rawNumber.equals("private", ignoreCase = true) || rawNumber.equals("unknown", ignoreCase = true)) {
            if (prefs.isBlockUnknownPrivateEnabled) {
                blockCall(callDetails, "Nomor Privat / Tersembunyi", "Nomor Tidak Dikenal", prefs, dbHelper)
                return
            }
        }

        // Case 2: Allow all saved contacts (Priority whitelist)
        if (rawNumber.isNotEmpty() && prefs.isAllowAllContactsEnabled) {
            val isContact = ContactHelper.isNumberInContacts(applicationContext, rawNumber)
            if (isContact) {
                allowCall(callDetails)
                return
            }
        }

        // Case 3: Whitelist list
        if (rawNumber.isNotEmpty() && dbHelper.isWhitelisted(rawNumber)) {
            allowCall(callDetails)
            return
        }

        // Case 4: Block all calls mode
        if (prefs.isBlockAllCallsEnabled) {
            blockCall(callDetails, "Mode Blokir Semua Panggilan", rawNumber, prefs, dbHelper)
            return
        }

        // Case 5: Match against rules (STARTS_WITH, CONTAINS, ENDS_WITH, EXACT)
        if (rawNumber.isNotEmpty()) {
            val (matched, ruleDescription) = dbHelper.checkMatchingRule(rawNumber)
            if (matched) {
                blockCall(callDetails, ruleDescription ?: "Aturan Blokir", rawNumber, prefs, dbHelper)
                return
            }
        }

        // Default: Allow call
        allowCall(callDetails)
    }

    private fun allowCall(callDetails: Call.Details) {
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipNotification(false)
            .setSkipCallLog(false)
            .build()
        respondToCall(callDetails, response)
    }

    private fun blockCall(
        callDetails: Call.Details,
        reason: String,
        displayNumber: String,
        prefs: PreferenceManager,
        dbHelper: DatabaseHelper
    ) {
        val response = CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipNotification(true)
            .setSkipCallLog(false)
            .build()
        respondToCall(callDetails, response)

        val timestamp = System.currentTimeMillis()
        dbHelper.insertHistory(displayNumber, timestamp, reason)

        if (prefs.isNotificationEnabled) {
            showNotification(displayNumber, reason, timestamp)
        }
    }

    private fun showNotification(phoneNumber: String, reason: String, timestamp: Long) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifikasi saat panggilan spam berhasil diblokir"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notifId = (timestamp % 100000).toInt()

        // Open App Intent
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            this,
            notifId,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Quick Action: Whitelist Number
        val whitelistIntent = Intent(this, CallNotificationReceiver::class.java).apply {
            action = CallNotificationReceiver.ACTION_WHITELIST
            putExtra(CallNotificationReceiver.EXTRA_PHONE_NUMBER, phoneNumber)
            putExtra(CallNotificationReceiver.EXTRA_NOTIFICATION_ID, notifId)
        }
        val whitelistPendingIntent = PendingIntent.getBroadcast(
            this,
            notifId + 1,
            whitelistIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Panggilan Spam Diblokir")
            .setContentText("$phoneNumber ($reason)")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Panggilan dari $phoneNumber telah ditolak.\nAlasan: $reason")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(appPendingIntent)
            .addAction(R.drawable.ic_check, "Whitelist Nomor", whitelistPendingIntent)

        notificationManager.notify(notifId, builder.build())
    }
}
