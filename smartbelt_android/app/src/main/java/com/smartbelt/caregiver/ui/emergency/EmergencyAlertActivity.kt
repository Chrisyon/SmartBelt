package com.smartbelt.caregiver.ui.emergency

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.app.KeyguardManager
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.smartbelt.caregiver.data.api.ApiClient
import com.smartbelt.caregiver.data.model.EmergencyRequest
import com.smartbelt.caregiver.data.model.KontakDarurat
import com.smartbelt.caregiver.databinding.ActivityEmergencyAlertBinding
import com.smartbelt.caregiver.service.EmergencyMonitoringService
import com.smartbelt.caregiver.ui.main.MainActivity
import com.smartbelt.caregiver.ui.map.MapTrackingActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class EmergencyAlertActivity : AppCompatActivity() {

    companion object {
        var isActivityOpen = false
        var isEmergencyActive = false
        var isTemporarilyDismissed = false
        var dismissedKejadianId: Long = 0L
        var lastCancelledTime = 0L
        var activeIncidentIntent: Intent? = null
        var isCountdownFinished: Boolean = false
        var activeKejadianId: Long = 0L
        var hasTriggeredInitialCall = false

        // Unified monotonic timing & duration
        var activeIncidentStartTime: Long = 0L
        var activeCountdownDuration: Int = 30
    }

    private lateinit var binding: ActivityEmergencyAlertBinding
    private var countDownTimer: CountDownTimer? = null
    private var countdownJob: Job? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var syncJob: Job? = null
    private var callLoopJob: Job? = null
    private var isAdminOrCaregiver = false

    private val emergencyContacts = mutableListOf<KontakDarurat>()
    private var currentContactIndex = 0
    private var currentCycleCount = 1

    private fun parseWaktuKejadianMillis(waktuStr: String?): Long {
        if (waktuStr.isNullOrEmpty()) return System.currentTimeMillis()
        val formats = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS"
        )
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.getDefault())
                val d = sdf.parse(waktuStr)
                if (d != null) {
                    val time = d.time
                    val now = System.currentTimeMillis()
                    if (kotlin.math.abs(now - time) < 900_000L) {
                        return time
                    }
                }
            } catch (e: Exception) {}
        }
        return System.currentTimeMillis()
    }

    private val requestCallPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "Izin panggilan darurat otomatis aktif", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isActivityOpen = true
        isEmergencyActive = true
        isTemporarilyDismissed = false
        dismissedKejadianId = 0L
        activeIncidentIntent = intent

        // Bangunkan layar & tampilkan di atas layar kunci jika darurat berbunyi
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        binding = ActivityEmergencyAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Bersihkan banner notifikasi sistem agar tidak menutupi tombol back di atas
        EmergencyMonitoringService.dismissEmergencyNotification(this)

        val sessionManager = com.smartbelt.caregiver.data.pref.SessionManager(this)
        val userRole = sessionManager.getRole()?.uppercase() ?: "KELUARGA"
        isAdminOrCaregiver = (userRole == "ADMIN" || userRole == "CAREGIVER")

        // Minta izin CALL_PHONE hanya untuk Keluarga/Pengguna (Admin tidak butuh auto-call)
        if (!isAdminOrCaregiver && ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestCallPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }

        val incomingKejadianId = intent.getIntExtra("ID_KEJADIAN", 0).toLong()
        val statusKejadian = intent.getStringExtra("STATUS_KEJADIAN") ?: ""
        val residentName = intent.getStringExtra("NAMA_PENGHUNI") ?: "Bapak Yohannes Sitompul"
        val roomLocation = intent.getStringExtra("ALAMAT_KAMAR") ?: "Kamar A-01 • Wisma Panti INAKAKA"
        val countdownDuration = intent.getIntExtra("COUNTDOWN_DETIK", 0)

        // Tentukan durasi countdown (prioritas: parameter intent jika > 0, atau activeCountdownDuration, fallback 30)
        if (countdownDuration > 0) {
            activeCountdownDuration = countdownDuration
        } else if (activeCountdownDuration <= 0) {
            val sessionDuration = sessionManager.getCountdownDuration()
            activeCountdownDuration = if (sessionDuration > 0) sessionDuration else 30
        }

        val now = System.currentTimeMillis()

        // Insiden baru jika: belum pernah tercatat waktu mulai ATAU terjadi insiden baru yang berbeda ID definitifnya
        val isNewIncident = (activeIncidentStartTime == 0L) ||
                (incomingKejadianId != 0L && activeKejadianId != 0L && incomingKejadianId != activeKejadianId)

        if (isNewIncident) {
            if (incomingKejadianId != 0L) {
                activeKejadianId = incomingKejadianId
            }
            activeIncidentStartTime = now
            isCountdownFinished = (statusKejadian == "AUTO_CALL")
            hasTriggeredInitialCall = (statusKejadian == "AUTO_CALL")
        } else {
            // Insiden lanjutan (misal user buka tinjau setelah tutup sementara)
            if (incomingKejadianId != 0L) {
                activeKejadianId = incomingKejadianId
            }
            if (statusKejadian == "AUTO_CALL") {
                isCountdownFinished = true
                hasTriggeredInitialCall = true
            } else {
                // Periksa apakah waktu countdown sudah terlewati selama layar ditutup
                val elapsed = ((now - activeIncidentStartTime) / 1000).toInt()
                if (elapsed >= activeCountdownDuration) {
                    isCountdownFinished = true
                } else {
                    isCountdownFinished = false
                }
            }
        }
        isEmergencyActive = true

        val rollThreshold = intent.getIntExtra("ROLL_COUNT", sessionManager.getThresholdRollCount())
        val detectedSvm = intent.getDoubleExtra("DETECTED_SVM", sessionManager.getThresholdSvm().toDouble())

        val outDistance = intent.getDoubleExtra("OUT_OF_GEOFENCE_DISTANCE", 0.0)

        val alertType = when (val rawType = intent.getStringExtra("JENIS_KEJADIAN")) {
            "TILT_EKSTREM" -> "${rollThreshold}x BADAN MIRING"
            "POSISI_MIRING_LAMA" -> "POSISI MIRING TERLALU LAMA"
            "PANIC_BUTTON" -> "PANGGILAN SOS DARURAT"
            "JATUH" -> "BENTURAN JATUH TERDETEKSI"
            "HEART_RATE_RENDAH" -> "DETAK JANTUNG KRITIS (RENDAH)"
            "HEART_RATE_TINGGI" -> "DETAK JANTUNG KRITIS (TINGGI)"
            "SPO2_RENDAH" -> "SATURASI OKSIGEN KRITIS"
            "KELUAR_ZONA_AMAN" -> "🚨 KELUAR DARI ZONA AMAN!"
            else -> rawType ?: "PERINGATAN DARURAT"
        }

        binding.tvResidentNameAlert.text = residentName
        binding.tvLocationAlert.text = roomLocation
        binding.tvAlertType.text = alertType
        when (intent.getStringExtra("JENIS_KEJADIAN")) {
            "TILT_EKSTREM" -> binding.tvSvmImpact.text = "Pola: ${rollThreshold}x Badan Miring"
            "POSISI_MIRING_LAMA" -> binding.tvSvmImpact.text = "Pola: Posisi Badan Miring Terlalu Lama (> ${sessionManager.getDurasiMiringDetik()} detik)"
            "HEART_RATE_RENDAH" -> binding.tvSvmImpact.text = "Peringatan: Detak Jantung di Bawah Batas Normal"
            "HEART_RATE_TINGGI" -> binding.tvSvmImpact.text = "Peringatan: Detak Jantung di Atas Batas Normal"
            "SPO2_RENDAH" -> binding.tvSvmImpact.text = "Peringatan: Saturasi Oksigen di Bawah Batas Normal"
            "KELUAR_ZONA_AMAN" -> {
                if (outDistance > 0) {
                    binding.tvSvmImpact.text = String.format(Locale.US, "Jarak: %.0fm dari Batas Aman (Radius: %dm)", outDistance, sessionManager.getMaxDistanceMeters())
                } else {
                    binding.tvSvmImpact.text = "Peringatan: Lansia Berada di Luar Batas Radius Aman!"
                }
                binding.tvLocationAlert.text = "📍 Posisi Sabuk Terdeteksi di Luar Batas Aman"
            }
            "PANIC_BUTTON" -> binding.tvSvmImpact.text = "Peringatan: Tombol Darurat SOS Ditekan"
            else -> binding.tvSvmImpact.text = String.format(Locale.US, "Kekuatan Benturan: SVM %.2fg", detectedSvm)
        }



        // Tampilan khusus Admin: Sembunyikan panel auto-call, tombol telepon, dan tombol batalkan alarm
        if (isAdminOrCaregiver) {
            binding.cardCallSequence.visibility = View.GONE
            binding.btnCallCaregiver.visibility = View.GONE
            binding.btnCancelAlarm.visibility = View.GONE // ADMIN TIDAK BERHAK MEMBATALKAN ALARM
            val mapParams = binding.btnViewMap.layoutParams as? android.widget.LinearLayout.LayoutParams
            mapParams?.marginStart = 0
            binding.btnViewMap.layoutParams = mapParams
            binding.btnViewMap.text = "📍 LIHAT PETA LOKASI LANSIA"
            binding.btnBackEmergency.text = "Tutup (Pantau di Beranda)"
        } else {
            binding.cardCallSequence.visibility = View.VISIBLE
            binding.btnCallCaregiver.visibility = View.VISIBLE
            binding.btnCancelAlarm.visibility = View.VISIBLE
        }

        // 1. Back button (Tutup sementara tanpa mematikan alarm alat)
        binding.btnBackEmergency.setOnClickListener {
            finishWithoutCancellingAlarm()
        }

        onBackPressedDispatcher.addCallback(this) {
            finishWithoutCancellingAlarm()
        }

        startAlarmSoundAndVibration()

        if (statusKejadian == "AUTO_CALL" || isCountdownFinished) {
            onCountdownComplete()
        } else {
            startCountdownLoop()
        }

        startEmergencySync()
        if (!isAdminOrCaregiver) {
            loadEmergencyContacts()
        }
        fetchDynamicDeviceConfig()

        // 2. Tombol Batalkan Alarm
        binding.btnCancelAlarm.setOnClickListener {
            cancelAlarmAction()
        }

        binding.btnCallCaregiver.setOnClickListener {
            triggerCallToCurrentContact()
        }

        binding.btnDialCurrent.setOnClickListener {
            triggerCallToCurrentContact()
        }

        binding.btnNextContact.setOnClickListener {
            advanceToNextContact(manual = true)
        }

        binding.btnViewMap.setOnClickListener {
            val session = com.smartbelt.caregiver.data.pref.SessionManager(this)
            val fallbackLat = session.getDefaultLatitude()
            val fallbackLng = session.getDefaultLongitude()

            val rawLat = intent.getDoubleExtra("LATITUDE", fallbackLat)
            val rawLng = intent.getDoubleExtra("LONGITUDE", fallbackLng)

            val devId = intent.getIntExtra("ID_PERANGKAT", 4)
            val cachedPair = MapTrackingActivity.lastKnownValidCoords[devId]

            val finalLat = if (rawLat != -3.695436 && rawLat != 0.0) {
                rawLat
            } else if (cachedPair != null) {
                cachedPair.first
            } else {
                fallbackLat
            }

            val finalLng = if (rawLng != 128.181427 && rawLng != 0.0) {
                rawLng
            } else if (cachedPair != null) {
                cachedPair.second
            } else {
                fallbackLng
            }

            val mapIntent = Intent(this, MapTrackingActivity::class.java).apply {
                putExtra("EXTRA_LAT", finalLat)
                putExtra("EXTRA_LNG", finalLng)
                putExtra("EXTRA_NAME", residentName)
                putExtra("EXTRA_LOCATION_NAME", roomLocation)
                putExtra("EXTRA_ALERT_TYPE", alertType)
                putExtra("EXTRA_IS_EMERGENCY", true)
            }
            startActivity(mapIntent)
        }
    }

    private fun fetchDynamicDeviceConfig() {
        val idPerangkat = intent.getIntExtra("ID_PERANGKAT", 0)
        val idUserPenghuni = intent.getIntExtra("ID_USER_PENGHUNI", 0)

        if (idPerangkat <= 0 && idUserPenghuni <= 0) return

        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(this@EmergencyAlertActivity)
                val res = if (idPerangkat > 0) {
                    api.getPengaturanByPerangkat(idPerangkat)
                } else {
                    api.getPengaturanByUser(idUserPenghuni)
                }

                if (res.isSuccessful && res.body()?.data != null) {
                    val cfg = res.body()!!.data!!
                    val dynamicCountdown = cfg.durasiCountdown
                    val dynamicRoll = cfg.thresholdRollCount ?: 5

                    if (dynamicCountdown != null && dynamicCountdown > 0) {
                        activeCountdownDuration = dynamicCountdown
                    }

                    val dynamicDurasi = cfg.durasiMiringDetik ?: 5
                    if (intent.getStringExtra("JENIS_KEJADIAN") == "TILT_EKSTREM") {
                        binding.tvSvmImpact.text = "Pola: ${dynamicRoll}x Badan Miring"
                    } else if (intent.getStringExtra("JENIS_KEJADIAN") == "POSISI_MIRING_LAMA") {
                        binding.tvSvmImpact.text = "Pola: Posisi Badan Miring Terlalu Lama (> ${dynamicDurasi} detik)"
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun finishWithoutCancellingAlarm() {
        isEmergencyActive = true
        isActivityOpen = false
        isTemporarilyDismissed = true
        dismissedKejadianId = activeKejadianId
        countdownJob?.cancel()
        countdownJob = null
        countDownTimer?.cancel()
        countDownTimer = null
        syncJob?.cancel()
        callLoopJob?.cancel()
        try {
            ringtone?.stop()
            vibrator?.cancel()
        } catch (e: Exception) {}
        Toast.makeText(
            this,
            "Layar darurat ditutup sementara. Alarm alat tetap berbunyi & dapat ditinjau di beranda.",
            Toast.LENGTH_LONG
        ).show()
        if (isTaskRoot) {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }

    private fun loadEmergencyContacts() {
        val targetPenghuniId = intent.getIntExtra("ID_PENGHUNI", 1)
        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(this@EmergencyAlertActivity)
                val res = withContext(Dispatchers.IO) {
                    api.getKontakByPenghuni(targetPenghuniId)
                }
                if (res.isSuccessful && !res.body()?.data.isNullOrEmpty()) {
                    emergencyContacts.clear()
                    emergencyContacts.addAll(res.body()!!.data!!.sortedBy { it.prioritas ?: 99 })
                    updateContactCardUi()
                }
            } catch (e: Exception) {
                // Fallback default
            }
        }
    }

    private fun updateContactCardUi() {
        if (emergencyContacts.isEmpty()) {
            binding.tvCurrentCallingContact.text = "Panggilan Otomatis: Posko Wisma (081234567890)..."
            return
        }

        val safeIndex = currentContactIndex.coerceIn(0, emergencyContacts.size - 1)
        val contact = emergencyContacts[safeIndex]
        val prio = contact.prioritas ?: (safeIndex + 1)
        binding.tvCurrentCallingContact.text =
            "Panggilan Otomatis: Prioritas #$prio: ${contact.namaKontak} (${contact.noTelepon}) [Putaran ke-$currentCycleCount]"
        binding.tvCallCycleStatus.text = "Putaran #$currentCycleCount"
    }

    private fun triggerCallToCurrentContact() {
        if (isAdminOrCaregiver) return // Admin TIDAK PERNAH melakukan auto-call

        val contactName: String
        val phone = if (emergencyContacts.isNotEmpty()) {
            val safeIndex = currentContactIndex.coerceIn(0, emergencyContacts.size - 1)
            val c = emergencyContacts[safeIndex]
            contactName = c.namaKontak ?: "Kontak Darurat"
            c.noTelepon ?: "081234567890"
        } else {
            contactName = "Posko Wisma"
            "081234567890"
        }

        updateContactCardUi()

        val hasCallPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        Toast.makeText(
            this,
            "📞 Otomatis Menghubungi: $contactName ($phone)...",
            Toast.LENGTH_SHORT
        ).show()

        if (hasCallPermission) {
            try {
                // ACTION_CALL: Panggilan keluar OTOMATIS langsung tanpa harus tekan tombol hijau
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$phone")
                }
                startActivity(callIntent)
            } catch (e: Exception) {
                // Fallback ke ACTION_DIAL jika izin dibatasi OS tertentu
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phone")
                }
                startActivity(dialIntent)
            }
        } else {
            requestCallPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phone")
            }
            startActivity(dialIntent)
        }

        startAutoCallLoopTimer()
    }

    private fun advanceToNextContact(manual: Boolean = false) {
        if (emergencyContacts.isEmpty()) return

        currentContactIndex++
        if (currentContactIndex >= emergencyContacts.size) {
            currentContactIndex = 0
            currentCycleCount++
        }

        updateContactCardUi()
        triggerCallToCurrentContact()
    }

    private fun startAutoCallLoopTimer() {
        callLoopJob?.cancel()
        callLoopJob = lifecycleScope.launch {
            // Beri jeda 25 detik, jika nomor saat ini belum merespon, otomatis beralih memanggil nomor prioritas berikutnya
            delay(25000)
            if (isActive && isEmergencyActive) {
                Toast.makeText(
                    this@EmergencyAlertActivity,
                    "Mengalihkan otomatis ke nomor prioritas berikutnya...",
                    Toast.LENGTH_SHORT
                ).show()
                advanceToNextContact(manual = false)
            }
        }
    }

    private var hasConfirmedActive = false

    private fun startEmergencySync() {
        syncJob?.cancel()
        syncJob = lifecycleScope.launch {
            delay(1000)
            var wasEverActiveOnServer = false
            while (isActive) {
                try {
                    val api = ApiClient.getApiService(this@EmergencyAlertActivity)

                    // 1. Sinkronisasi status AUTO_CALL dan countdown secara real-time dari alat
                    val activeRes = api.getActiveEmergencies()
                    if (activeRes.isSuccessful) {
                        val activeList = activeRes.body()?.data
                        if (!activeList.isNullOrEmpty()) {
                            val targetItem = if (activeKejadianId != 0L) {
                                activeList.firstOrNull { it.idKejadian?.toLong() == activeKejadianId } ?: activeList.first()
                            } else {
                                activeList.first()
                            }

                            if (targetItem.idKejadian != null) {
                                activeKejadianId = targetItem.idKejadian.toLong()
                                wasEverActiveOnServer = true
                            }

                            // Sinkronkan durasi countdown alat secara dinamis jika tersedia
                            if (targetItem.durasiCountdown != null && targetItem.durasiCountdown > 0) {
                                activeCountdownDuration = targetItem.durasiCountdown
                            }

                            if (targetItem.statusKejadian == "AUTO_CALL") {
                                onCountdownComplete()
                            }
                        } else if (wasEverActiveOnServer) {
                            // Alarm darurat yang sebelumnya aktif di server telah dibatalkan (misal via tombol sabuk GP18)
                            isEmergencyActive = false
                            isTemporarilyDismissed = false
                            dismissedKejadianId = 0L
                            activeIncidentStartTime = 0L
                            isCountdownFinished = false
                            hasTriggeredInitialCall = false
                            activeKejadianId = 0L
                            lastCancelledTime = System.currentTimeMillis()
                            stopAlarm()
                            Toast.makeText(
                                this@EmergencyAlertActivity,
                                "Alarm darurat telah dibatalkan via tombol sabuk (GP18)",
                                Toast.LENGTH_SHORT
                            ).show()
                            if (isTaskRoot) {
                                startActivity(Intent(this@EmergencyAlertActivity, MainActivity::class.java))
                            }
                            finish()
                            break
                        }
                    }

                    // 2. Cek status aktif via endpoint isEmergencyActive hanya jika sudah terkonfirmasi aktif di server
                    if (wasEverActiveOnServer) {
                        val res = api.isEmergencyActive("28:CD:C1:10:41:9F")
                        if (res.isSuccessful) {
                            val isActiveOnServer = res.body()?.data == true
                            if (!isActiveOnServer) {
                                isEmergencyActive = false
                                isTemporarilyDismissed = false
                                dismissedKejadianId = 0L
                                activeIncidentStartTime = 0L
                                isCountdownFinished = false
                                hasTriggeredInitialCall = false
                                activeKejadianId = 0L
                                lastCancelledTime = System.currentTimeMillis()
                                stopAlarm()
                                Toast.makeText(
                                    this@EmergencyAlertActivity,
                                    "Alarm darurat telah dibatalkan via tombol sabuk (GP18)",
                                    Toast.LENGTH_SHORT
                                ).show()
                                if (isTaskRoot) {
                                    startActivity(Intent(this@EmergencyAlertActivity, MainActivity::class.java))
                                }
                                finish()
                                break
                            }
                        }
                    }
                } catch (e: Exception) {}
                delay(800)
            }
        }
    }

    private fun startCountdownLoop() {
        countdownJob?.cancel()
        countDownTimer?.cancel()
        countDownTimer = null

        countdownJob = lifecycleScope.launch {
            while (isActive && isEmergencyActive) {
                if (isCountdownFinished) {
                    onCountdownComplete()
                    break
                }

                val now = System.currentTimeMillis()
                val startTime = if (activeIncidentStartTime > 0L) activeIncidentStartTime else now
                val elapsed = ((now - startTime) / 1000).toInt()
                val remaining = activeCountdownDuration - elapsed

                if (remaining <= 0) {
                    onCountdownComplete()
                    break
                } else {
                    binding.tvCountdown.text = "$remaining"
                    if (isAdminOrCaregiver) {
                        binding.tvCountdownLabel.text = "Hitung Mundur Alarm Sabuk Lansia:"
                    } else {
                        binding.tvCountdownLabel.text = "Panggilan & SMS Darurat Otomatis dalam:"
                    }
                }
                delay(300)
            }
        }
    }

    private fun onCountdownComplete() {
        isCountdownFinished = true
        countdownJob?.cancel()
        countdownJob = null
        countDownTimer?.cancel()
        countDownTimer = null

        binding.tvCountdown.text = "0"
        if (isAdminOrCaregiver) {
            binding.tvCountdownLabel.text = "🚨 Waktu Habis • Silakan Pantau Lokasi di Peta"
        } else {
            binding.tvCountdownLabel.text = "🚨 Waktu Habis • Panggilan Darurat Aktif"
            if (!hasTriggeredInitialCall) {
                hasTriggeredInitialCall = true
                Toast.makeText(this@EmergencyAlertActivity, "Waktu habis! Memulai panggilan darurat otomatis...", Toast.LENGTH_SHORT).show()
                triggerCallToCurrentContact()
            }
        }
    }

    private fun startAlarmSoundAndVibration() {
        try {
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(applicationContext, alertUri)
            ringtone?.play()

            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 400, 200, 400, 200, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {}
    }

    private fun stopAlarm() {
        countdownJob?.cancel()
        countdownJob = null
        countDownTimer?.cancel()
        countDownTimer = null
        callLoopJob?.cancel()
        callLoopJob = null
        try {
            ringtone?.stop()
            vibrator?.cancel()
        } catch (e: Exception) {}
    }

    private fun cancelAlarmAction() {
        if (isAdminOrCaregiver) {
            Toast.makeText(this, "Admin tidak memiliki izin untuk membatalkan alarm lansia.", Toast.LENGTH_SHORT).show()
            return
        }

        val wasFinished = isCountdownFinished
        isEmergencyActive = false
        isTemporarilyDismissed = false
        dismissedKejadianId = 0L
        activeIncidentStartTime = 0L
        isCountdownFinished = false
        hasTriggeredInitialCall = false
        activeKejadianId = 0L
        lastCancelledTime = System.currentTimeMillis()
        stopAlarm()
        syncJob?.cancel()
        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(this@EmergencyAlertActivity)
                api.cancelEmergency("28:CD:C1:10:41:9F")
            } catch (e: Exception) {}

            val msg = if (wasFinished) "✅ Alarm darurat dihentikan (Buzzer Dimatikan)." else "✅ Alarm darurat berhasil dibatalkan."
            Toast.makeText(this@EmergencyAlertActivity, msg, Toast.LENGTH_SHORT).show()
            if (isTaskRoot) {
                startActivity(Intent(this@EmergencyAlertActivity, MainActivity::class.java))
            }
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityOpen = true
        EmergencyMonitoringService.dismissEmergencyNotification(this)

        if (!isEmergencyActive) {
            finish()
            return
        }

        startAlarmSoundAndVibration()

        val now = System.currentTimeMillis()
        val startTime = if (activeIncidentStartTime > 0L) activeIncidentStartTime else now
        val elapsed = ((now - startTime) / 1000).toInt()
        val remaining = activeCountdownDuration - elapsed

        if (isCountdownFinished || remaining <= 0) {
            onCountdownComplete()
        } else {
            isCountdownFinished = false
            binding.tvCountdown.text = "$remaining"
            startCountdownLoop()
        }
        startEmergencySync()
    }

    override fun onPause() {
        super.onPause()
        countdownJob?.cancel()
        countdownJob = null
        countDownTimer?.cancel()
        countDownTimer = null
        syncJob?.cancel()
        syncJob = null
        try {
            ringtone?.stop()
            vibrator?.cancel()
        } catch (e: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        isActivityOpen = false
        countdownJob?.cancel()
        countdownJob = null
        countDownTimer?.cancel()
        countDownTimer = null
        syncJob?.cancel()
        callLoopJob?.cancel()
        try {
            ringtone?.stop()
            vibrator?.cancel()
        } catch (e: Exception) {}
    }
}
