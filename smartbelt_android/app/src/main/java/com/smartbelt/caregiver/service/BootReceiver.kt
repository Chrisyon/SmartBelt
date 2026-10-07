package com.smartbelt.caregiver.service
//Langsung membangunkan
//sistem pemantau darurat secara otomatis begitu HP perawat selesai di-restart / baru dinyalakan.

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smartbelt.caregiver.data.pref.SessionManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == "android.intent.action.QUICKBOOT_POWERON") {
            val session = SessionManager(context)
            if (session.isLoggedIn()) {
                EmergencyMonitoringService.startService(context)
            }
        }
    }
}
