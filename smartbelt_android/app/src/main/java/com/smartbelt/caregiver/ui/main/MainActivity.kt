package com.smartbelt.caregiver.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.smartbelt.caregiver.R
import com.smartbelt.caregiver.service.EmergencyMonitoringService
import com.smartbelt.caregiver.data.api.ApiClient
import com.smartbelt.caregiver.data.model.DataSensor
import com.smartbelt.caregiver.data.model.EmergencyRequest
import com.smartbelt.caregiver.data.model.KejadianDarurat
import com.smartbelt.caregiver.data.model.Penghuni
import com.smartbelt.caregiver.data.pref.SessionManager
import com.smartbelt.caregiver.databinding.ActivityMainBinding
import com.smartbelt.caregiver.databinding.ActivityMainAdminBinding
import com.smartbelt.caregiver.databinding.ActivityMainLansiaBinding
import com.smartbelt.caregiver.databinding.ActivityDeviceSettingsBinding
import com.smartbelt.caregiver.ui.emergency.EmergencyAlertActivity
import com.smartbelt.caregiver.ui.history.IncidentHistoryActivity
import com.smartbelt.caregiver.ui.map.MapTrackingActivity
import com.smartbelt.caregiver.ui.penghuni.PenghuniDetailActivity
import com.smartbelt.caregiver.ui.penghuni.PenghuniListActivity
import com.smartbelt.caregiver.ui.profile.ProfileActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var bindingKeluarga: ActivityMainBinding? = null
    private var bindingAdmin: ActivityMainAdminBinding? = null
    private var bindingLansia: ActivityMainLansiaBinding? = null
    private lateinit var sessionManager: SessionManager
    private var pollingJob: Job? = null
    private var isEmergencyOpen = false
    private var currentRole: String = "KELUARGA"
    private var currentActiveEmergency: KejadianDarurat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
        currentRole = sessionManager.getRole()?.uppercase() ?: "KELUARGA"

        when (currentRole) {
            "ADMIN", "CAREGIVER" -> {
                bindingAdmin = ActivityMainAdminBinding.inflate(layoutInflater)
                setContentView(bindingAdmin!!.root)
                setupAdminUi()
            }
            "PENGGUNA_BELT", "LANSIA" -> {
                bindingLansia = ActivityMainLansiaBinding.inflate(layoutInflater)
                setContentView(bindingLansia!!.root)
                setupLansiaUi()
            }
            else -> { // KELUARGA
                bindingKeluarga = ActivityMainBinding.inflate(layoutInflater)
                setContentView(bindingKeluarga!!.root)
                setupKeluargaUi()
            }
        }

        // Minta izin Notifikasi untuk Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // Pastikan EmergencyMonitoringService selalu aktif
        try {
            EmergencyMonitoringService.startService(this)
        } catch (e: Exception) {}
    }

    private var currentLinkedPenghuni: Penghuni? = null

    override fun onResume() {
        super.onResume()
        isEmergencyOpen = false
        lastFallDetectedTime = 0L
        if (EmergencyAlertActivity.isEmergencyActive || currentActiveEmergency != null) {
            showEmergencyBanner(null)
            updateSosButtonsState(true)
        } else {
            hideEmergencyBanner()
            updateSosButtonsState(false)
        }
        fetchLinkedResidentData()
        startTelemetryPolling()
    }

    override fun onPause() {
        super.onPause()
        pollingJob?.cancel()
    }

    private fun fetchLinkedResidentData() {
        val userId = sessionManager.getUserId()
        if (userId <= 0) return

        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(this@MainActivity)
                if (currentRole == "KELUARGA") {
                    val res = api.getPenghuniByKeluarga(userId)
                    if (res.isSuccessful && !res.body()?.data.isNullOrEmpty()) {
                        val penghuni = res.body()?.data!!.first()
                        currentLinkedPenghuni = penghuni
                        bindingKeluarga?.tvResidentName?.text = penghuni.namaLengkap
                        bindingKeluarga?.tvRoomInfo?.text = "${penghuni.alamatKamar ?: "Wisma INAKAKA"} • Smart-Belt #${penghuni.idPenghuni}"
                    }
                } else if (currentRole == "PENGGUNA_BELT" || currentRole == "LANSIA") {
                    val res = api.getPenghuniByLansia(userId)
                    if (res.isSuccessful && res.body()?.data != null) {
                        val penghuni = res.body()?.data!!
                        currentLinkedPenghuni = penghuni
                    }
                }

                // Fetch dynamic device config (Countdown, SVM, Roll Count, HR, SpO2, Geofence)
                val configRes = api.getPengaturanByUser(userId)
                if (configRes.isSuccessful && configRes.body()?.data != null) {
                    val cfg = configRes.body()?.data!!
                    sessionManager.saveDeviceConfig(
                        svm = (cfg.thresholdSvm ?: 2.70).toFloat(),
                        rollCount = cfg.thresholdRollCount ?: 5,
                        durasiMiringDetik = cfg.durasiMiringDetik ?: 5,
                        countdown = cfg.durasiCountdown ?: 30,
                        minHr = cfg.minHeartRate ?: 60,
                        maxHr = cfg.maxHeartRate ?: 120,
                        minSpo2 = cfg.minSpo2 ?: 95,
                        locName = cfg.lokasiDefaultNama ?: "Wisma Panti INAKAKA Ambon",
                        defaultLat = cfg.defaultLatitude ?: -3.695436,
                        defaultLng = cfg.defaultLongitude ?: 128.181427,
                        maxDistMeters = cfg.maxDistanceMeters ?: 100
                    )
                }
            } catch (e: Exception) {}
        }
    }


    // --- SETUP ROLE 1: ADMIN & CAREGIVER ---
    private fun setupAdminUi() {
        val b = bindingAdmin ?: return
        b.tvWelcomeAdmin.text = "Halo, ${sessionManager.getNamaLengkap()}"
        b.tvAdminBadge.text = "${currentRole} • Pemantau Wisma INAKAKA"

        b.swipeRefreshAdmin.setOnRefreshListener { 
            fetchDashboardData()
            fetchLinkedResidentData()
        }
        b.btnAdminProfile.setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }
        b.cardAdminPenghuni.setOnClickListener { startActivity(Intent(this, PenghuniListActivity::class.java)) }
        b.cardAdminMap.setOnClickListener { startActivity(Intent(this, MapTrackingActivity::class.java)) }
        b.cardAdminHistory.setOnClickListener { startActivity(Intent(this, IncidentHistoryActivity::class.java)) }
        b.cardAdminSettings.setOnClickListener {
            startActivity(Intent(this, com.smartbelt.caregiver.ui.admin.UserListActivity::class.java))
        }
        b.btnAdminLihatEmergency.setOnClickListener { openEmergencyScreen(currentActiveEmergency) }
    }

    // --- SETUP ROLE 2: KELUARGA PENGGUNA ---
    private fun setupKeluargaUi() {
        val b = bindingKeluarga ?: return
        b.tvWelcomeUser.text = "Halo, ${sessionManager.getNamaLengkap()}"
        b.tvUserRole.text = "KELUARGA • Memantau Anggota Keluarga"

        fetchLinkedResidentData()

        b.swipeRefresh.setOnRefreshListener { 
            fetchDashboardData()
            fetchLinkedResidentData()
        }
        b.btnProfile.setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }
        b.cardMenuMap.setOnClickListener { startActivity(Intent(this, MapTrackingActivity::class.java)) }
        b.cardMenuHistory.setOnClickListener { startActivity(Intent(this, IncidentHistoryActivity::class.java)) }
        b.cardMenuKontak.setOnClickListener {
            val intent = Intent(this, PenghuniDetailActivity::class.java).apply {
                putExtra("ID_PENGHUNI", currentLinkedPenghuni?.idPenghuni ?: 1)
                putExtra("NAMA_PENGHUNI", currentLinkedPenghuni?.namaLengkap ?: "Lansia Terpantau")
                putExtra("KAMAR", currentLinkedPenghuni?.alamatKamar ?: "Kamar Wisma")
                putExtra("RIWAYAT", currentLinkedPenghuni?.riwayatPenyakit ?: "Riwayat kesehatan lansia")
            }
            startActivity(intent)
        }
        b.cardMenuSos.setOnClickListener { 
            if (EmergencyAlertActivity.isEmergencyActive || currentActiveEmergency != null) {
                Toast.makeText(this, "⚠️ Alarm darurat sedang aktif! Membuka layar darurat...", Toast.LENGTH_SHORT).show()
                openEmergencyScreen(currentActiveEmergency)
            } else {
                triggerManualSos()
            }
        }
        b.btnManualSos.setOnClickListener { 
            if (EmergencyAlertActivity.isEmergencyActive || currentActiveEmergency != null) {
                Toast.makeText(this, "⚠️ Alarm darurat sedang aktif! Membuka layar darurat...", Toast.LENGTH_SHORT).show()
                openEmergencyScreen(currentActiveEmergency)
            } else {
                triggerManualSos()
            }
        }
        b.btnLihatEmergency.setOnClickListener { openEmergencyScreen(currentActiveEmergency) }
    }

    // --- SETUP ROLE 3: PENGGUNA BELT / LANSIA ---
    private fun setupLansiaUi() {
        val b = bindingLansia ?: return
        b.tvWelcomeLansia.text = "Halo, ${sessionManager.getNamaLengkap()}"

        fetchLinkedResidentData()

        b.swipeRefreshLansia.setOnRefreshListener { 
            fetchDashboardData()
            fetchLinkedResidentData()
        }
        b.btnLansiaProfile.setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }
        b.cardLansiaSos.setOnClickListener { 
            if (EmergencyAlertActivity.isEmergencyActive || currentActiveEmergency != null) {
                Toast.makeText(this, "⚠️ Alarm darurat sedang aktif! Membuka layar darurat...", Toast.LENGTH_SHORT).show()
                openEmergencyScreen(currentActiveEmergency)
            } else {
                triggerManualSos()
            }
        }
        b.cardLansiaCallCaregiver.setOnClickListener {
            val intent = Intent(this, PenghuniDetailActivity::class.java).apply {
                putExtra("ID_PENGHUNI", currentLinkedPenghuni?.idPenghuni ?: 1)
                putExtra("NAMA_PENGHUNI", currentLinkedPenghuni?.namaLengkap ?: "Lansia Pengguna")
                putExtra("KAMAR", currentLinkedPenghuni?.alamatKamar ?: "Kamar Wisma")
                putExtra("RIWAYAT", currentLinkedPenghuni?.riwayatPenyakit ?: "Riwayat kesehatan lansia")
            }
            startActivity(intent)
        }
    }

    private fun startTelemetryPolling() {
        pollingJob?.cancel()
        pollingJob = lifecycleScope.launch {
            while (isActive) {
                fetchDashboardData()
                delay(1000)
            }
        }
    }

    private fun fetchDashboardData() {
        lifecycleScope.launch {
            val api = ApiClient.getApiService(this@MainActivity)

            // 1. Check Active Emergencies
            try {
                val emergencyRes = api.getActiveEmergencies()
                if (emergencyRes.isSuccessful && emergencyRes.body()?.success == true) {
                    val activeList = emergencyRes.body()?.data
                    // Filter: Admin/Caregiver memantau semua lansia, sedangkan Keluarga/Lansia memantau lansia yang terikat dengannya (fallback ke active item jika belum ter-cache)
                    val targetActive = if (currentRole == "ADMIN" || currentRole == "CAREGIVER") {
                        activeList?.firstOrNull()
                    } else {
                        val matched = activeList?.firstOrNull { item ->
                            val itemPenghuniId = item.penghuni?.idPenghuni
                            itemPenghuniId != null && itemPenghuniId == currentLinkedPenghuni?.idPenghuni
                        }
                        matched ?: activeList?.firstOrNull()
                    }

                    if (targetActive != null) {
                        val active = targetActive
                        currentActiveEmergency = active
                        val isRecentlyCancelled = (System.currentTimeMillis() - EmergencyAlertActivity.lastCancelledTime) < 8000L
                        if (!isRecentlyCancelled) {
                            if (active.durasiCountdown != null && active.durasiCountdown > 0) {
                                EmergencyAlertActivity.activeCountdownDuration = active.durasiCountdown
                            }
                            if (EmergencyAlertActivity.activeIncidentStartTime == 0L) {
                                EmergencyAlertActivity.activeIncidentStartTime = System.currentTimeMillis()
                            }
                            if (active.idKejadian != null && active.idKejadian > 0) {
                                val incomingId = active.idKejadian.toLong()
                                if (EmergencyAlertActivity.dismissedKejadianId != 0L && incomingId != EmergencyAlertActivity.dismissedKejadianId) {
                                    EmergencyAlertActivity.isTemporarilyDismissed = false
                                    EmergencyAlertActivity.dismissedKejadianId = 0L
                                }
                                EmergencyAlertActivity.activeKejadianId = incomingId
                            }

                            val isCurrentDismissed = EmergencyAlertActivity.isTemporarilyDismissed
                            if (!EmergencyAlertActivity.isActivityOpen && !EmergencyAlertActivity.isEmergencyActive && !isCurrentDismissed && (active.statusKejadian == "COUNTDOWN" || active.statusKejadian == "TERDETEKSI" || active.statusKejadian == "AUTO_CALL" || active.statusKejadian == "TERJADI")) {
                                openEmergencyScreen(active)
                            } else {
                                showEmergencyBanner(active)
                            }
                        } else {
                            hideEmergencyBanner()
                        }
                    } else {
                        currentActiveEmergency = null
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
                        hideEmergencyBanner()
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }

            // 2. Fetch Sensor Telemetry (Device ID 4)
            try {
                val sensorRes = api.getLatestSensorData(4)
                if (sensorRes.isSuccessful && sensorRes.body()?.success == true) {
                    sensorRes.body()?.data?.let { updateSensorUi(it) }
                }
            } catch (e: Exception) {
                // Ignore
            }

            bindingKeluarga?.swipeRefresh?.isRefreshing = false
            bindingAdmin?.swipeRefreshAdmin?.isRefreshing = false
            bindingLansia?.swipeRefreshLansia?.isRefreshing = false
        }
    }

    private var lastFallDetectedTime = 0L

    private fun updateSensorUi(sensor: DataSensor) {
        val svm = sensor.svm ?: 1.0
        val pitch = sensor.pitch ?: 0.0
        val roll = sensor.roll ?: 0.0

        val absPitch = kotlin.math.abs(pitch)
        val absRoll = kotlin.math.abs(roll)
        val isMiring = absPitch > 35.0 || absRoll > 35.0
        val isTumbang = absPitch > 50.0 || absRoll > 50.0
        val currentThresholdSvm = sessionManager.getThresholdSvm().toDouble()
        val isFall = (svm >= currentThresholdSvm) || (svm >= (currentThresholdSvm - 0.25) && isTumbang)

        val minHr = sessionManager.getMinHeartRate()
        val maxHr = sessionManager.getMaxHeartRate()
        val minSpo2 = sessionManager.getMinSpo2()
        val maxDist = sessionManager.getMaxDistanceMeters()
        val defaultLat = sessionManager.getDefaultLatitude()
        val defaultLng = sessionManager.getDefaultLongitude()
        val locName = sessionManager.getLokasiDefaultNama()

        val hr = sensor.heartRate ?: 0
        val spo2 = sensor.spo2 ?: 0
        val lat = sensor.latitude ?: defaultLat
        val lng = sensor.longitude ?: defaultLng

        // Cek anomali detak jantung & saturasi oksigen
        val isHrMinDanger = (hr in 1 until minHr)
        val isHrMaxDanger = (hr > maxHr)
        val isSpo2Danger = (spo2 in 1 until minSpo2)

        // Cek Geofence (Batas Jarak Jauh)
        val distMeters = calculateDistanceInMeters(lat, lng, defaultLat, defaultLng)
        val isOutOfGeofence = (sensor.latitude != null && sensor.latitude != 0.0 && distMeters > maxDist)

        // Update UI metrics & Card Colors on Keluarga Dashboard
        bindingKeluarga?.let { b ->
            val currentThresholdSvm = sessionManager.getThresholdSvm()
            b.tvSvmValue.text = String.format(Locale.US, "%.2fg", svm)
            b.tvBatasG.text = String.format(Locale.US, "Batas  : %.1fg", currentThresholdSvm)
            b.tvPitchValue.text = String.format(Locale.US, "%+.1f°", pitch)
            b.tvRollValue.text = String.format(Locale.US, "%+.1f°", roll)

            b.tvHeartRateValue.text = if (hr > 0) "$hr BPM" else "-- BPM"
            b.tvSpo2Value.text = if (spo2 > 0) "$spo2 %" else "-- %"

            // Card Detak Jantung: Berubah warna menjadi danger (merah) jika di bawah batas
            if (isHrMinDanger) {
                b.cardHeartRate.setCardBackgroundColor(android.graphics.Color.parseColor("#FEE2E2"))
                b.cardHeartRate.strokeColor = android.graphics.Color.parseColor("#EF4444")
                b.tvHeartRateValue.setTextColor(android.graphics.Color.parseColor("#DC2626"))
            } else {
                b.cardHeartRate.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.bg_card))
                b.cardHeartRate.strokeColor = androidx.core.content.ContextCompat.getColor(this, R.color.stroke_card)
                b.tvHeartRateValue.setTextColor(android.graphics.Color.parseColor("#DC2626"))
            }

            // Card Detak Jantung: Berubah warna menjadi danger (merah) jika di bawah batas
            if (isHrMaxDanger) {
                b.cardHeartRate.setCardBackgroundColor(android.graphics.Color.parseColor("#FEE2E2"))
                b.cardHeartRate.strokeColor = android.graphics.Color.parseColor("#EF4444")
                b.tvHeartRateValue.setTextColor(android.graphics.Color.parseColor("#DC2626"))
            } else {
                b.cardHeartRate.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.bg_card))
                b.cardHeartRate.strokeColor = androidx.core.content.ContextCompat.getColor(this, R.color.stroke_card)
                b.tvHeartRateValue.setTextColor(android.graphics.Color.parseColor("#DC2626"))
            }

            // Card SpO2: Berubah warna menjadi danger (merah) jika di bawah batas
            if (isSpo2Danger) {
                b.cardSpo2.setCardBackgroundColor(android.graphics.Color.parseColor("#FEE2E2"))
                b.cardSpo2.strokeColor = android.graphics.Color.parseColor("#EF4444")
                b.tvSpo2Value.setTextColor(android.graphics.Color.parseColor("#DC2626"))
            } else {
                b.cardSpo2.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.bg_card))
                b.cardSpo2.strokeColor = androidx.core.content.ContextCompat.getColor(this, R.color.stroke_card)
                b.tvSpo2Value.setTextColor(android.graphics.Color.parseColor("#2563EB"))
            }
        }

        // Update UI metrics & Card Colors on Lansia Dashboard
        bindingLansia?.let { b ->
            b.tvLansiaHeartRate.text = if (hr > 0) "$hr BPM" else "-- BPM"
            b.tvLansiaSpo2.text = if (spo2 > 0) "$spo2 %" else "-- %"

            if (isHrMinDanger) {
                b.cardLansiaHeartRate?.setCardBackgroundColor(android.graphics.Color.parseColor("#FEE2E2"))
                b.cardLansiaHeartRate?.strokeColor = android.graphics.Color.parseColor("#EF4444")
                b.tvLansiaHeartRate.setTextColor(android.graphics.Color.parseColor("#DC2626"))
            } else {
                b.cardLansiaHeartRate?.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.bg_heart_rate))
                b.cardLansiaHeartRate?.strokeColor = androidx.core.content.ContextCompat.getColor(this, R.color.stroke_heart_rate)
                b.tvLansiaHeartRate.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_heart_rate))
            }

            if (isHrMaxDanger) {
                b.cardLansiaHeartRate?.setCardBackgroundColor(android.graphics.Color.parseColor("#FEE2E2"))
                b.cardLansiaHeartRate?.strokeColor = android.graphics.Color.parseColor("#EF4444")
                b.tvLansiaHeartRate.setTextColor(android.graphics.Color.parseColor("#DC2626"))
            } else {
                b.cardLansiaHeartRate?.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.bg_heart_rate))
                b.cardLansiaHeartRate?.strokeColor = androidx.core.content.ContextCompat.getColor(this, R.color.stroke_heart_rate)
                b.tvLansiaHeartRate.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_heart_rate))
            }

            if (isSpo2Danger) {
                b.cardLansiaSpo2?.setCardBackgroundColor(android.graphics.Color.parseColor("#FEE2E2"))
                b.cardLansiaSpo2?.strokeColor = android.graphics.Color.parseColor("#EF4444")
                b.tvLansiaSpo2.setTextColor(android.graphics.Color.parseColor("#DC2626"))
            } else {
                b.cardLansiaSpo2?.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.bg_spo2))
                b.cardLansiaSpo2?.strokeColor = androidx.core.content.ContextCompat.getColor(this, R.color.stroke_spo2)
                b.tvLansiaSpo2.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_spo2))
            }
        }

        val now = System.currentTimeMillis()
        if (isFall || isHrMinDanger || isHrMaxDanger || isSpo2Danger || isOutOfGeofence) {
            lastFallDetectedTime = now
            val isRecentlyCancelled = (System.currentTimeMillis() - EmergencyAlertActivity.lastCancelledTime) < 15000L
            val isCurrentDismissed = EmergencyAlertActivity.isTemporarilyDismissed || EmergencyAlertActivity.isEmergencyActive
            if (!isRecentlyCancelled && !isEmergencyOpen && !EmergencyAlertActivity.isActivityOpen && !isCurrentDismissed) {
                val jenis = when {
                    isOutOfGeofence -> "KELUAR_ZONA_AMAN"
                    isFall -> "JATUH"
                    isHrMinDanger -> "HEART_RATE_RENDAH"
                    isHrMaxDanger -> "HEART_RATE_TINGGI"
                    else -> "SPO2_RENDAH"
                }
                lifecycleScope.launch {
                    try {
                        val triggerReq = EmergencyRequest(
                            macAddress = "28:CD:C1:10:41:9F",
                            jenisKejadian = jenis,
                            statusKejadian = "COUNTDOWN",
                            svmValue = svm,
                            latitude = lat,
                            longitude = lng,
                            alamatLokasi = if (isOutOfGeofence) "Keluar Zona Aman (%.0fm dari %s)".format(distMeters, locName) else "Sensor Trigger"
                        )
                        val api = ApiClient.getApiService(this@MainActivity)
                        val triggerRes = api.triggerEmergency(triggerReq)
                        val saved = if (triggerRes.isSuccessful) triggerRes.body()?.data else null
                        openEmergencyScreen(saved, svm, jenis, outDistance = distMeters)
                    } catch (e: Exception) {
                        openEmergencyScreen(null, svm, jenis, outDistance = distMeters)
                    }
                }
            } else if (isCurrentDismissed) {
                currentActiveEmergency?.let { showEmergencyBanner(it) }
            }
        }

        val inEmergencyCooldown = (now - lastFallDetectedTime) < 15000L

        // Update Fall & Posture Status Pill for KELUARGA
        bindingKeluarga?.let { b ->
            when {
                inEmergencyCooldown || isFall || isHrMinDanger || isHrMaxDanger || isSpo2Danger || isOutOfGeofence -> {
                    b.layoutFallStatus.setBackgroundResource(R.drawable.bg_status_danger)
                    b.ivStatusIcon.setImageResource(R.drawable.ic_warning_triangle)
                    b.ivStatusIcon.setColorFilter(android.graphics.Color.parseColor("#DC2626"))
                    
                    val title = when {
                        isFall -> "🚨 BENTURAN JATUH TERDETEKSI!"
                        isHrMinDanger -> "⚠️ DETAK JANTUNG RENDAH (< $minHr BPM)!"
                        isHrMaxDanger -> "⚠️ DETAK JANTUNG TINGGI (< $maxHr BPM)!"
                        isSpo2Danger -> "⚠️ SATURASI OKSIGEN RENDAH (< $minSpo2%)!"
                        isOutOfGeofence -> "🚨 KELUAR DARI ZONA AMAN (> ${maxDist}m)!"
                        else -> "MASA TENANG 15 DETIK"
                    }
                    val desc = when {
                        isHrMinDanger -> "Terbaca: $hr BPM (Batas normal minimal: $minHr BPM)"
                        isHrMaxDanger -> "Terbaca: $hr BPM (Batas normal maximal: $maxHr BPM)"
                        isSpo2Danger -> "Terbaca: $spo2% (Batas normal minimal: $minSpo2%)"
                        isOutOfGeofence -> String.format(Locale.US, "Jarak: %.0fm dari %s (Batas: %dm)", distMeters, locName, maxDist)
                        else -> String.format(Locale.US, "Benturan kuat (SVM: %.2fg | Pitch: %+.1f° | Roll: %+.1f°)", svm, pitch, roll)
                    }
                    b.tvFallStatusTitle.text = title
                    b.tvFallStatusTitle.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                    b.tvFallStatusDesc.text = desc
                }
                isMiring -> {
                    b.layoutFallStatus.setBackgroundResource(R.drawable.bg_status_warning)
                    b.ivStatusIcon.setImageResource(R.drawable.ic_warning_triangle)
                    b.ivStatusIcon.setColorFilter(android.graphics.Color.parseColor("#D97706"))
                    b.tvFallStatusTitle.text = "POSTUR TUBUH MIRING / CONDONG"
                    b.tvFallStatusTitle.setTextColor(android.graphics.Color.parseColor("#D97706"))
                    b.tvFallStatusDesc.text = String.format(Locale.US, "Kemiringan terdeteksi (Pitch: %+.1f° | Roll: %+.1f°)", pitch, roll)
                }
                else -> {
                    b.layoutFallStatus.setBackgroundResource(R.drawable.bg_status_safe)
                    b.ivStatusIcon.setImageResource(R.drawable.ic_check_circle)
                    b.ivStatusIcon.setColorFilter(android.graphics.Color.parseColor("#16A34A"))
                    b.tvFallStatusTitle.text = "KONDISI NORMAL (TEGAK & AMAN)"
                    b.tvFallStatusTitle.setTextColor(android.graphics.Color.parseColor("#16A34A"))
                    b.tvFallStatusDesc.text = String.format(Locale.US, "Postur tubuh tegak & stabil (Pitch: %+.1f° | Roll: %+.1f°)", pitch, roll)
                }
            }
        }

        // Update Posture Status for LANSIA
        bindingLansia?.let { b ->
            when {
                inEmergencyCooldown || isFall || isHrMinDanger || isHrMaxDanger || isSpo2Danger || isOutOfGeofence -> {
                    val title = when {
                        isHrMinDanger -> "DETAK JANTUNG RENDAH!"
                        isHrMaxDanger -> "DETAK JANTUNG RENDAH!"
                        isSpo2Danger -> "OKSIGEN DARAH RENDAH!"
                        isOutOfGeofence -> "MELEWATI BATAS AMAN!"
                        else -> "BENTURAN JATUH TERDETEKSI!"
                    }
                    b.tvLansiaStatusTitle.text = title
                    b.tvLansiaStatusTitle.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                    b.tvLansiaStatusDesc.text = "Alarm berbunyi & sinyal dikirim ke perawat wisma"
                }
                isMiring -> {
                    b.tvLansiaStatusTitle.text = "POSTUR TUBUH MIRING"
                    b.tvLansiaStatusTitle.setTextColor(android.graphics.Color.parseColor("#D97706"))
                    b.tvLansiaStatusDesc.text = "Perbaiki postur duduk/berdiri Anda"
                }
                else -> {
                    b.tvLansiaStatusTitle.text = "SABUK AKTIF & TEGAK AMAN"
                    b.tvLansiaStatusTitle.setTextColor(android.graphics.Color.parseColor("#16A34A"))
                    b.tvLansiaStatusDesc.text = "Postur tubuh Anda stabil & seimbang"
                }
            }
        }
    }

    private fun calculateDistanceInMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val results = FloatArray(1)
        // Fungsi resmi bawaan dari Android OS SDK:
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0].toDouble() // Hasil jarak langsung keluar dalam satuan Meter!

    }

//    private fun calculateDistanceInMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
//        val r = 6371000.0 // Radius bumi dalam meter
//        val dLat = Math.toRadians(lat2 - lat1)
//        val dLon = Math.toRadians(lon2 - lon1)
//        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
//                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
//                Math.sin(dLon / 2) * Math.sin(dLon / 2)
//        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
//        return r * c
//    }

    private fun showEmergencyBanner(emergency: KejadianDarurat?) {
        val target = emergency ?: currentActiveEmergency
        val title = "🚨 ALARM ${target?.jenisKejadian ?: "DARURAT"} MASIH AKTIF!"
        val sub = "Buzzer menyala. Ketuk di sini untuk meninjau kembali & hubungi bantuan."

        bindingKeluarga?.cardEmergencyBanner?.visibility = View.VISIBLE
        bindingKeluarga?.tvEmergencyTitle?.text = title
        bindingKeluarga?.tvEmergencySubtitle?.text = sub
        bindingKeluarga?.cardEmergencyBanner?.setOnClickListener {
            openEmergencyScreen(target ?: currentActiveEmergency)
        }
        bindingKeluarga?.btnLihatEmergency?.setOnClickListener {
            openEmergencyScreen(target ?: currentActiveEmergency)
        }

        bindingAdmin?.cardAdminEmergencyBanner?.visibility = View.VISIBLE
        bindingAdmin?.tvAdminEmergencyTitle?.text = title
        bindingAdmin?.tvAdminEmergencySubtitle?.text = sub
        bindingAdmin?.cardAdminEmergencyBanner?.setOnClickListener {
            openEmergencyScreen(target ?: currentActiveEmergency)
        }
        bindingAdmin?.btnAdminLihatEmergency?.setOnClickListener {
            openEmergencyScreen(target ?: currentActiveEmergency)
        }

        updateSosButtonsState(true)
    }

    private fun hideEmergencyBanner() {
        if (!EmergencyAlertActivity.isEmergencyActive && currentActiveEmergency == null) {
            bindingKeluarga?.cardEmergencyBanner?.visibility = View.GONE
            bindingAdmin?.cardAdminEmergencyBanner?.visibility = View.GONE
            updateSosButtonsState(false)
        }
    }

    private fun updateSosButtonsState(isAlarmActive: Boolean) {
        bindingKeluarga?.let { bk ->
            if (isAlarmActive) {
                bk.btnManualSos.isEnabled = false
                bk.btnManualSos.alpha = 0.5f
                bk.btnManualSos.text = "🚨 ALARM SEDANG AKTIF"
                bk.cardMenuSos.isEnabled = false
                bk.cardMenuSos.alpha = 0.5f
            } else {
                bk.btnManualSos.isEnabled = true
                bk.btnManualSos.alpha = 1.0f
                bk.btnManualSos.text = "KIRIM SINYAL DARURAT"
                bk.cardMenuSos.isEnabled = true
                bk.cardMenuSos.alpha = 1.0f
            }
        }

        bindingLansia?.let { bl ->
            if (isAlarmActive) {
                bl.cardLansiaSos.setCardBackgroundColor(android.graphics.Color.parseColor("#7F1D1D"))
                bl.cardLansiaSos.alpha = 0.85f
                bl.tvLansiaSosTitle.text = "🚨 ALARM SEDANG AKTIF"
                bl.tvLansiaSosSubtitle.text = "Ketuk untuk membuka layar darurat & pemantauan"
            } else {
                bl.cardLansiaSos.setCardBackgroundColor(android.graphics.Color.parseColor("#DC2626"))
                bl.cardLansiaSos.alpha = 1.0f
                bl.tvLansiaSosTitle.text = "MINTA BANTUAN SOS"
                bl.tvLansiaSosSubtitle.text = "Ketuk di sini jika terjatuh atau butuh perawat"
            }
        }
    }

    private fun openEmergencyScreen(
        emergency: KejadianDarurat?,
        detectedSvm: Double? = null,
        alertType: String? = null,
        outDistance: Double? = null
    ) {
        if (EmergencyAlertActivity.isActivityOpen) return
        val isRecentlyCancelled = (System.currentTimeMillis() - EmergencyAlertActivity.lastCancelledTime) < 8000L
        if (isRecentlyCancelled) return

        EmergencyAlertActivity.isTemporarilyDismissed = false
        EmergencyAlertActivity.dismissedKejadianId = 0L
        EmergencyAlertActivity.isActivityOpen = true
        EmergencyAlertActivity.isEmergencyActive = true
        isEmergencyOpen = true
        val targetEmergency = emergency ?: currentActiveEmergency
        val actualSvm = detectedSvm ?: targetEmergency?.svmValue ?: 0.0
        val residentName = targetEmergency?.penghuni?.namaLengkap 
            ?: currentLinkedPenghuni?.namaLengkap 
            ?: "Bapak Yohannes Sitompul"
        val roomLocation = targetEmergency?.penghuni?.alamatKamar 
            ?: currentLinkedPenghuni?.alamatKamar 
            ?: "Kamar Wisma"

        val idPenghuni = targetEmergency?.penghuni?.idPenghuni ?: currentLinkedPenghuni?.idPenghuni ?: 1
        val idPerangkat = targetEmergency?.perangkat?.idPerangkat ?: 0
        val idUserPenghuni = sessionManager.getUserId()

        val finalType = alertType ?: targetEmergency?.jenisKejadian ?: "BENTURAN TERDETEKSI"

        val intent = Intent(this, EmergencyAlertActivity::class.java).apply {
            putExtra("ID_KEJADIAN", targetEmergency?.idKejadian ?: 0)
            putExtra("ID_PENGHUNI", idPenghuni)
            putExtra("ID_PERANGKAT", idPerangkat)
            putExtra("ID_USER_PENGHUNI", idUserPenghuni)
            putExtra("JENIS_KEJADIAN", finalType)
            putExtra("STATUS_KEJADIAN", targetEmergency?.statusKejadian ?: "")
            putExtra("WAKTU_KEJADIAN", targetEmergency?.waktuKejadian ?: "")
            putExtra("NAMA_PENGHUNI", residentName)
            putExtra("ALAMAT_KAMAR", "$roomLocation • Wisma INAKAKA")
            putExtra("LATITUDE", targetEmergency?.latitude ?: -3.695436)
            putExtra("LONGITUDE", targetEmergency?.longitude ?: 128.181427)
            putExtra("ALAMAT_LOKASI", targetEmergency?.alamatLokasi ?: "$roomLocation • Wisma INAKAKA")
            val targetCountdown = targetEmergency?.durasiCountdown
                ?: (if (EmergencyAlertActivity.activeCountdownDuration > 0) EmergencyAlertActivity.activeCountdownDuration else null)
                ?: sessionManager.getCountdownDuration()
            putExtra("COUNTDOWN_DETIK", targetCountdown)
            putExtra("ROLL_COUNT", sessionManager.getThresholdRollCount())
            putExtra("DETECTED_SVM", actualSvm)
            if (outDistance != null && outDistance > 0) {
                putExtra("OUT_OF_GEOFENCE_DISTANCE", outDistance)
            }
        }
        startActivity(intent)
    }

    private fun triggerManualSos() {
        if (EmergencyAlertActivity.isEmergencyActive || currentActiveEmergency != null) {
            Toast.makeText(this@MainActivity, "⚠️ Alarm darurat sedang berlangsung! Buka tinjauan alarm untuk memantau.", Toast.LENGTH_SHORT).show()
            openEmergencyScreen(currentActiveEmergency)
            return
        }

        lifecycleScope.launch {
            try {
                Toast.makeText(this@MainActivity, "Memulai Panggilan SOS Darurat...", Toast.LENGTH_SHORT).show()
                val api = ApiClient.getApiService(this@MainActivity)
                val req = EmergencyRequest(
                    macAddress = "28:CD:C1:10:41:9F",
                    jenisKejadian = "PANIC_BUTTON",
                    statusKejadian = "COUNTDOWN",
                    svmValue = 3.50,
                    latitude = -3.695436,
                    longitude = 128.181427,
                    alamatLokasi = "Kamar A-01"
                )
                val res = api.triggerEmergency(req)
                val savedKejadian = if (res.isSuccessful) res.body()?.data else null
                openEmergencyScreen(savedKejadian)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Gagal mengirim sinyal SOS", Toast.LENGTH_SHORT).show()
                openEmergencyScreen(null)
            }
        }
    }
}
