package com.fajar.spamcallblocker.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.fajar.spamcallblocker.data.DatabaseHelper

class CallNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_WHITELIST = "com.fajar.spamcallblocker.ACTION_WHITELIST"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_WHITELIST) {
            val phone = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: return
            val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

            DatabaseHelper.getInstance(context).insertWhitelist(phone, "From Notification")

            if (notifId != -1) {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notifId)
            }

            Toast.makeText(context, "$phone added to Whitelist", Toast.LENGTH_SHORT).show()
        }
    }
}
