package com.smartbelt.caregiver

//Mengatur Tema Layar (Dark Mode / Normal) Mengecek pengaturan simpanan, lalu mengatur warna aplikasi ke mode gelap atau terang.
//Mengingat Layar yang Sedang Dibuka (currentActivity) Memantau halaman mana yang lagi aktif dilihat perawat di HP secara real-time.
//Langsung Menyalakan Satpam Darurat Jika perawat sudah login, aplikasi otomatis menyalakan EmergencyMonitoringService agar siap memantau insiden jatuh lansia.

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import com.smartbelt.caregiver.data.pref.SessionManager
import com.smartbelt.caregiver.service.EmergencyMonitoringService
import java.lang.ref.WeakReference

class SmartBeltApp : Application() {

    companion object {
        private var currentActivityRef: WeakReference<Activity>? = null

        val currentActivity: Activity?
            get() = currentActivityRef?.get()

        var isAppInForeground: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Dark Mode Configuration
        val session = SessionManager(this)
        if (session.isDarkMode()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        // 2. Global Activity Lifecycle Callback untuk intersepsi layar darurat di halaman manapun
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {}

            override fun onActivityResumed(activity: Activity) {
                currentActivityRef = WeakReference(activity)
                isAppInForeground = true
            }

            override fun onActivityPaused(activity: Activity) {
                if (currentActivityRef?.get() == activity) {
                    isAppInForeground = false
                }
            }

            override fun onActivityStopped(activity: Activity) {}

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivityRef?.get() == activity) {
                    currentActivityRef = null
                }
            }
        })

        // 3. Jalankan EmergencyMonitoringService jika pengguna sudah login
        if (session.isLoggedIn()) {
            try {
                EmergencyMonitoringService.startService(this)
            } catch (e: Exception) {}
        }
    }
}
