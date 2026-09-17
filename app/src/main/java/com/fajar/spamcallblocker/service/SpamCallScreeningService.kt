package com.fajar.spamcallblocker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
        private const val CHANNEL_NAME = "Blocked Calls"
    }

    override fun onScreenCall(callDetails: Call.Details) {
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            allowCall(callDetails)
            return
        }

        val prefs = PreferenceManager(applicationContext)
        val dbHelper = DatabaseHelper.getInstance(applicationContext)

        if (!prefs.isServiceEnabled) {
            allowCall(callDetails)
            return
        }

        val rawNumber = callDetails.handle?.schemeSpecificPart?.trim() ?: ""

        // Private / hidden number
        if (rawNumber.isEmpty() || rawNumber.equals("private", ignoreCase = true) || rawNumber.equals("unknown", ignoreCase = true)) {
            if (prefs.isBlockUnknownPrivateEnabled) {
                blockCall(callDetails, "Private Number", "Private Number", prefs, dbHelper)
                return
            }
        }

        // Allow saved contacts
        if (rawNumber.isNotEmpty() && prefs.isAllowAllContactsEnabled) {
            if (ContactHelper.isNumberInContacts(applicationContext, rawNumber)) {
                allowCall(callDetails)
                return
            }
        }

        // Whitelist
        if (rawNumber.isNotEmpty() && dbHelper.isWhitelisted(rawNumber)) {
            allowCall(callDetails)
            return
        }

        // Block all mode
        if (prefs.isBlockAllCallsEnabled) {
            blockCall(callDetails, "Block All Calls mode is active", rawNumber, prefs, dbHelper)
            return
        }

        // Rule matching
        if (rawNumber.isNotEmpty()) {
            val (matched, ruleDescription) = dbHelper.checkMatchingRule(rawNumber)
            if (matched) {
                blockCall(callDetails, ruleDescription ?: "Spam Filter", rawNumber, prefs, dbHelper)
                return
            }
        }

        allowCall(callDetails)
    }

    private fun allowCall(callDetails: Call.Details) {
        respondToCall(callDetails, CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipNotification(false)
            .setSkipCallLog(false)
            .build())
    }

    private fun blockCall(
        callDetails: Call.Details,
        reason: String,
        displayNumber: String,
        prefs: PreferenceManager,
        dbHelper: DatabaseHelper
    ) {
        respondToCall(callDetails, CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipNotification(true)
            .setSkipCallLog(false)
            .build())

        val timestamp = System.currentTimeMillis()
        dbHelper.insertHistory(displayNumber, timestamp, reason)

        if (prefs.isNotificationEnabled) {
            showNotification(displayNumber, reason, prefs)
        }
    }

    private fun showNotification(phoneNumber: String, reason: String, prefs: PreferenceManager) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // minSdk 26 — channel always needed
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Notifications when spam calls are blocked"
        }
        notificationManager.createNotificationChannel(channel)

        val notifId = prefs.nextNotificationId()

        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(this, notifId, appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val whitelistIntent = Intent(this, CallNotificationReceiver::class.java).apply {
            action = CallNotificationReceiver.ACTION_WHITELIST
            putExtra(CallNotificationReceiver.EXTRA_PHONE_NUMBER, phoneNumber)
            putExtra(CallNotificationReceiver.EXTRA_NOTIFICATION_ID, notifId)
        }
        val whitelistPendingIntent = PendingIntent.getBroadcast(this, notifId + 1, whitelistIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Call Blocked")
            .setContentText(phoneNumber)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Blocked a call from $phoneNumber.\nMatched rule: $reason")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(appPendingIntent)

        // Only add Allow button if there is a real number
        if (phoneNumber != "Private Number" && phoneNumber != "Unknown Number") {
            builder.addAction(R.drawable.ic_check, "Allow Number", whitelistPendingIntent)
        }

        notificationManager.notify(notifId, builder.build())
    }
}
