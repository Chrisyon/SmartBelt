package com.smartbelt.caregiver.service

//Berjalan terus-menerus di latar belakang (Foreground Service) untuk memantau sinyal jatuh
//& memicu Panggilan Telepon Auto-Call, meskipun layar HP mati atau aplikasi sedang ditutup.

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.smartbelt.caregiver.R
import com.smartbelt.caregiver.SmartBeltApp
import com.smartbelt.caregiver.data.api.ApiClient
import com.smartbelt.caregiver.data.model.KejadianDarurat
import com.smartbelt.caregiver.data.pref.SessionManager
import com.smartbelt.caregiver.ui.emergency.EmergencyAlertActivity
import com.smartbelt.caregiver.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class EmergencyMonitoringService : Service() {

    companion object {
        const val CHANNEL_MONITORING = "smartbelt_monitoring_channel"
        const val CHANNEL_EMERGENCY = "smartbelt_emergency_channel"
        const val MONITORING_NOTIFICATION_ID = 1001
        const val EMERGENCY_NOTIFICATION_ID = 999

        @Volatile
        var isServiceRunning = false
            private set

        fun startService(context: Context) {
            val intent = Intent(context, EmergencyMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, EmergencyMonitoringService::class.java)
            context.stopService(intent)
        }

        fun dismissEmergencyNotification(context: Context) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(EMERGENCY_NOTIFICATION_ID)
            } catch (e: Exception) {}
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var monitoringJob: Job? = null
    private var isEmergencyNotificationShowing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannels()
        startServiceInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (monitoringJob == null || monitoringJob?.isActive == false) {
            startMonitoringLoop()
        }
        return START_STICKY
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Channel 1: Pemantauan Diam Persisten
            val monitorChannel = NotificationChannel(
                CHANNEL_MONITORING,
                "Pemantauan Smart-Belt Latar Belakang",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi status pemantauan keselamatan lansia secara real-time"
                setShowBadge(false)
            }
            nm.createNotificationChannel(monitorChannel)

            // Channel 2: Peringatan Darurat (Heads-Up & Sound & Vibration)
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val emergencyChannel = NotificationChannel(
                CHANNEL_EMERGENCY,
                "Alarm & Peringatan Darurat Lansia",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Memicu suara sirine & getaran saat lansia jatuh, keluar zona aman, atau meminta bantuan SOS"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setSound(alarmUri, audioAttributes)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            nm.createNotificationChannel(emergencyChannel)
        }
    }

    private fun startServiceInForeground() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_MONITORING)
            .setContentTitle("🛡️ Smart-Belt Memantau Keselamatan")
            .setContentText("Sistem aktif memantau kondisi & posisi lansia di latar belakang")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                MONITORING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(MONITORING_NOTIFICATION_ID, notification)
        }
    }

    private fun startMonitoringLoop() {
        monitoringJob?.cancel()
        monitoringJob = serviceScope.launch {
            while (isActive) {
                val session = SessionManager(this@EmergencyMonitoringService)
                if (!session.isLoggedIn()) {
                    stopSelf()
                    break
                }

                try {
                    checkEmergencies(session)
                } catch (e: Exception) {
                    // Abaikan transient network error
                }

                delay(2500)
            }
        }
    }

    private suspend fun checkEmergencies(session: SessionManager) {
        val api = ApiClient.getApiService(this)
        var detectedEmergency: KejadianDarurat? = null
        var isOutOfGeofence = false
        var outDistanceMeters = 0.0

        // 1. Cek Kejadian Darurat Aktif dari Server
        try {
            val emergencyRes = api.getActiveEmergencies()
            if (emergencyRes.isSuccessful && emergencyRes.body()?.success == true) {
                val activeList = emergencyRes.body()?.data
                if (!activeList.isNullOrEmpty()) {
                    var item = activeList.first()
                    // Jika koordinat insiden kosong/0/Ambon, dapatkan koordinat GPS sensor TERAKHIR yang valid dari history
                    if (item.latitude == null || item.latitude == 0.0 || item.latitude == -3.695436) {
                        try {
                            val historyRes = api.getSensorHistory(item.perangkat?.idPerangkat ?: 4)
                            if (historyRes.isSuccessful && !historyRes.body()?.data.isNullOrEmpty()) {
                                val validSensor = historyRes.body()!!.data!!.firstOrNull {
                                    it.latitude != null && it.latitude != 0.0 && it.latitude != -3.695436
                                }
                                if (validSensor != null) {
                                    item = item.copy(latitude = validSensor.latitude, longitude = validSensor.longitude)
                                }
                            }
                        } catch (e: Exception) {}
                    }

                    // Bersihkan insiden palsu KELUAR_ZONA_AMAN yang terpicu akibat koordinat dummy Ambon (-3.695436, 128.181427)
                    val itemLat = item.latitude ?: 0.0
                    val itemLng = item.longitude ?: 0.0
                    val defaultLat = session.getDefaultLatitude()
                    val defaultLng = session.getDefaultLongitude()
                    val distToHome = calculateDistanceInMeters(itemLat, itemLng, defaultLat, defaultLng)
                    val isDummyAmbonItem = (kotlin.math.abs(itemLat - (-3.695436)) < 0.05 && kotlin.math.abs(itemLng - 128.181427) < 0.05) || (distToHome > 50000.0)

                    if (item.jenisKejadian == "KELUAR_ZONA_AMAN" && isDummyAmbonItem) {
                        // Batalkan insiden palsu di backend
                        try {
                            api.cancelEmergency("28:CD:C1:10:41:9F")
                        } catch (e: Exception) {}
                        detectedEmergency = null
                    } else {
                        detectedEmergency = item
                    }
                }
            }
        } catch (e: Exception) {}

        // 2. Cek Telemetri GPS untuk Batas Luar Radius (Geofence Realistis)
        if (detectedEmergency == null) {
            try {
                val sensorRes = api.getLatestSensorData(4)
                if (sensorRes.isSuccessful && sensorRes.body()?.success == true) {
                    val sensor = sensorRes.body()?.data
                    if (sensor?.latitude != null && sensor.latitude != 0.0 && sensor.longitude != null && sensor.longitude != 0.0) {
                        val defaultLat = session.getDefaultLatitude()
                        val defaultLng = session.getDefaultLongitude()
                        val maxDist = session.getMaxDistanceMeters()
                        val dist = calculateDistanceInMeters(sensor.latitude, sensor.longitude, defaultLat, defaultLng)

                        val isDummyAmbon = (kotlin.math.abs(sensor.latitude - (-3.695436)) < 0.05 && kotlin.math.abs(sensor.longitude - 128.181427) < 0.05)
                        // Pemicu Geofence HANYA jika koordinat VALID (bukan Ambon/dummy) dan jarak realistis (maxDist < dist < 50km)
                        if (!isDummyAmbon && dist > maxDist && dist < 50000.0) {
                            val isRecentlyCancelled = (System.currentTimeMillis() - EmergencyAlertActivity.lastCancelledTime) < 15000L
                            if (!isRecentlyCancelled) {
                                isOutOfGeofence = true
                                outDistanceMeters = dist
                                val locName = session.getLokasiDefaultNama()
                                try {
                                    val triggerReq = com.smartbelt.caregiver.data.model.EmergencyRequest(
                                        macAddress = "28:CD:C1:10:41:9F",
                                        jenisKejadian = "KELUAR_ZONA_AMAN",
                                        statusKejadian = "COUNTDOWN",
                                        svmValue = sensor.svm ?: 1.0,
                                        latitude = sensor.latitude,
                                        longitude = sensor.longitude,
                                        alamatLokasi = "Keluar Zona Aman (%.0fm dari %s)".format(dist, locName)
                                    )
                                    val triggerRes = api.triggerEmergency(triggerReq)
                                    if (triggerRes.isSuccessful && triggerRes.body()?.data != null) {
                                        detectedEmergency = triggerRes.body()!!.data
                                    }
                                } catch (te: Exception) {}

                                if (detectedEmergency == null) {
                                    detectedEmergency = KejadianDarurat(
                                        idKejadian = 999999,
                                        jenisKejadian = "KELUAR_ZONA_AMAN",
                                        statusKejadian = "COUNTDOWN",
                                        waktuKejadian = null,
                                        svmValue = sensor.svm ?: 1.0,
                                        latitude = sensor.latitude,
                                        longitude = sensor.longitude,
                                        alamatLokasi = "Jarak: %.0fm dari %s (Batas: %dm)".format(dist, locName, maxDist),
                                        durasiCountdown = session.getCountdownDuration()
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {}
        }

        val isRecentlyCancelled = (System.currentTimeMillis() - EmergencyAlertActivity.lastCancelledTime) < 8000L

        if (detectedEmergency != null && !isRecentlyCancelled) {
            handleActiveEmergency(detectedEmergency, isOutOfGeofence, outDistanceMeters, session)
        } else {
            handleNoEmergency()
        }
    }

    private fun handleActiveEmergency(
        emergency: KejadianDarurat,
        isOutOfGeofence: Boolean,
        outDistance: Double,
        session: SessionManager
    ) {
        val now = System.currentTimeMillis()
        if (emergency.durasiCountdown != null && emergency.durasiCountdown > 0) {
            EmergencyAlertActivity.activeCountdownDuration = emergency.durasiCountdown
        }
        if (EmergencyAlertActivity.activeIncidentStartTime == 0L) {
            EmergencyAlertActivity.activeIncidentStartTime = now
        }
        if (emergency.idKejadian != null && emergency.idKejadian > 0) {
            val incomingId = emergency.idKejadian.toLong()
            if (EmergencyAlertActivity.dismissedKejadianId != 0L && incomingId != EmergencyAlertActivity.dismissedKejadianId) {
                EmergencyAlertActivity.isTemporarilyDismissed = false
                EmergencyAlertActivity.dismissedKejadianId = 0L
            }
            EmergencyAlertActivity.activeKejadianId = incomingId
        }
        EmergencyAlertActivity.isEmergencyActive = true

        // 1. Tampilkan Notifikasi Darurat Prioritas Tinggi HANYA jika aplikasi sedang TIDAK di depan layar (background / tertutup)
        if (!SmartBeltApp.isAppInForeground) {
            showEmergencyNotification(emergency, isOutOfGeofence, outDistance, session)
        } else {
            // Jika aplikasi sedang dibuka di layar, bersihkan notifikasi HP agar tidak menutupi tombol back / UI
            dismissEmergencyNotification()
        }

        // 2. Jika aplikasi sedang aktif di layar (misal user sedang di Map, Pengaturan, Penghuni, dll)
        // dan layar EmergencyAlertActivity belum terbuka, langsung munculkan EmergencyAlertActivity seketika!
        // KECUALI jika pengguna memilih "Tutup Sementara" (isTemporarilyDismissed == true)
        if (SmartBeltApp.isAppInForeground && !EmergencyAlertActivity.isActivityOpen && !EmergencyAlertActivity.isTemporarilyDismissed) {
            launchEmergencyAlertActivity(emergency, isOutOfGeofence, outDistance, session)
        }
    }

    private fun dismissEmergencyNotification() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(EMERGENCY_NOTIFICATION_ID)
            isEmergencyNotificationShowing = false
        } catch (e: Exception) {}
    }

    private fun handleNoEmergency() {
        dismissEmergencyNotification()

        if (!EmergencyAlertActivity.isActivityOpen) {
            val isRecentlyCancelled = (System.currentTimeMillis() - EmergencyAlertActivity.lastCancelledTime) < 8000L
            if (isRecentlyCancelled || !EmergencyAlertActivity.isEmergencyActive) {
                EmergencyAlertActivity.isEmergencyActive = false
                EmergencyAlertActivity.isTemporarilyDismissed = false
                EmergencyAlertActivity.dismissedKejadianId = 0L
                EmergencyAlertActivity.activeIncidentStartTime = 0L
                EmergencyAlertActivity.activeKejadianId = 0L
                EmergencyAlertActivity.isCountdownFinished = false
                EmergencyAlertActivity.hasTriggeredInitialCall = false
            }
        }
    }

    private fun buildAlertIntent(
        emergency: KejadianDarurat,
        isOutOfGeofence: Boolean,
        outDistance: Double,
        session: SessionManager
    ): Intent {
        val residentName = emergency.penghuni?.namaLengkap ?: "Bapak Yohannes Sitompul"
        val roomLocation = emergency.penghuni?.alamatKamar ?: "Kamar A-01 • Wisma INAKAKA"
        val targetCountdown = emergency.durasiCountdown
            ?: (if (EmergencyAlertActivity.activeCountdownDuration > 0) EmergencyAlertActivity.activeCountdownDuration else null)
            ?: session.getCountdownDuration()

        val alertType = if (isOutOfGeofence) "KELUAR_ZONA_AMAN" else (emergency.jenisKejadian ?: "PERINGATAN DARURAT")

        return Intent(this, EmergencyAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("ID_KEJADIAN", emergency.idKejadian ?: 0)
            putExtra("ID_PENGHUNI", emergency.penghuni?.idPenghuni ?: 1)
            putExtra("ID_PERANGKAT", emergency.perangkat?.idPerangkat ?: 4)
            putExtra("ID_USER_PENGHUNI", session.getUserId())
            putExtra("JENIS_KEJADIAN", alertType)
            putExtra("STATUS_KEJADIAN", emergency.statusKejadian ?: "")
            putExtra("WAKTU_KEJADIAN", emergency.waktuKejadian ?: "")
            putExtra("NAMA_PENGHUNI", residentName)
            putExtra("ALAMAT_KAMAR", "$roomLocation • Wisma INAKAKA")
            putExtra("LATITUDE", emergency.latitude ?: session.getDefaultLatitude())
            putExtra("LONGITUDE", emergency.longitude ?: session.getDefaultLongitude())
            putExtra("ALAMAT_LOKASI", emergency.alamatLokasi ?: "$roomLocation • Wisma INAKAKA")
            putExtra("COUNTDOWN_DETIK", targetCountdown)
            putExtra("ROLL_COUNT", session.getThresholdRollCount())
            putExtra("DETECTED_SVM", emergency.svmValue ?: session.getThresholdSvm().toDouble())
            putExtra("OUT_OF_GEOFENCE_DISTANCE", outDistance)
        }
    }

    private fun showEmergencyNotification(
        emergency: KejadianDarurat,
        isOutOfGeofence: Boolean,
        outDistance: Double,
        session: SessionManager
    ) {
        val alertIntent = buildAlertIntent(emergency, isOutOfGeofence, outDistance, session)
        val pendingIntent = PendingIntent.getActivity(
            this,
            EMERGENCY_NOTIFICATION_ID,
            alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val residentName = emergency.penghuni?.namaLengkap ?: "Lansia"
        val title = if (isOutOfGeofence) {
            "🚨 PERINGATAN ZONA: $residentName Keluar Radius Aman!"
        } else {
            "🚨 DARURAT: $residentName Butuh Bantuan Segera!"
        }

        val content = if (isOutOfGeofence) {
            "Jarak: %.0fm dari batas aman. Ketuk segera untuk membuka peta lokasi!".format(outDistance)
        } else {
            val jenis = emergency.jenisKejadian ?: "Insiden Bahaya"
            "$jenis terdeteksi! Ketuk sekarang untuk merespon dan memantau darurat."
        }

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Wake lock sementara untuk memastikan layar menyala saat notifikasi darurat masuk
        wakeUpScreenBriefly()

        val notification = NotificationCompat.Builder(this, CHANNEL_EMERGENCY)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(alarmUri)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setColor(0xFFDC2626.toInt())
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(EMERGENCY_NOTIFICATION_ID, notification)
        isEmergencyNotificationShowing = true
    }

    private fun launchEmergencyAlertActivity(
        emergency: KejadianDarurat,
        isOutOfGeofence: Boolean,
        outDistance: Double,
        session: SessionManager
    ) {
        serviceScope.launch(Dispatchers.Main) {
            val currentAct = SmartBeltApp.currentActivity
            if (currentAct != null && !currentAct.isFinishing && !currentAct.isDestroyed) {
                if (currentAct !is EmergencyAlertActivity) {
                    val alertIntent = buildAlertIntent(emergency, isOutOfGeofence, outDistance, session)
                    currentAct.startActivity(alertIntent)
                }
            } else {
                val alertIntent = buildAlertIntent(emergency, isOutOfGeofence, outDistance, session)
                startActivity(alertIntent)
            }
        }
    }

    private fun wakeUpScreenBriefly() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "SmartBelt:EmergencyWakeLock"
            )
            wakeLock.acquire(3000L)
        } catch (e: Exception) {}
    }

    private fun calculateDistanceInMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Radius bumi dalam meter
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        serviceScope.cancel()
    }
}
