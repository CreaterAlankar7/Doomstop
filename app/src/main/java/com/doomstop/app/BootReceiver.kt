package com.doomstop.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED &&
            Store.prefs(context).getBoolean("enabled", false)
        ) {
            context.startForegroundService(Intent(context, MonitorService::class.java))
        }
    }
}