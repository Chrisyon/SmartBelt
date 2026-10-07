package com.smartbelt.caregiver.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.smartbelt.caregiver.R
import com.smartbelt.caregiver.data.api.ApiClient
import com.smartbelt.caregiver.data.model.UpdatePengaturanRequest
import com.smartbelt.caregiver.data.pref.SessionManager
import com.smartbelt.caregiver.databinding.ActivityDeviceSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class DeviceSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceSettingsBinding
    private lateinit var sessionManager: SessionManager

    private var isUpdatingSvmFromSlider = false
    private var isUpdatingSvmFromText = false

    private var isUpdatingRollFromSlider = false
    private var isUpdatingRollFromText = false

    private var isUpdatingDurasiMiringFromSlider = false
    private var isUpdatingDurasiMiringFromText = false

    private var isUpdatingCountFromSlider = false
    private var isUpdatingCountFromText = false

    private var isUpdatingMinHrFromSlider = false
    private var isUpdatingMinHrFromText = false

    private var isUpdatingMaxHrFromSlider = false
    private var isUpdatingMaxHrFromText = false

    private var isUpdatingSpo2FromSlider = false
    private var isUpdatingSpo2FromText = false

    private var isUpdatingDistFromSlider = false
    private var isUpdatingDistFromText = false

    private var isMenit = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        binding.btnBack.setOnClickListener { finish() }

        setupTwoWaySynchronization()
        loadCurrentSettings()

        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }
    }

    private fun setupTwoWaySynchronization() {
        // 1. SVM Synchronization
        binding.sliderSvm.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !isUpdatingSvmFromText) {
                isUpdatingSvmFromSlider = true
                val formatted = String.format(Locale.US, "%.2f", value)
                binding.etThresholdSvm.setText(formatted)
                binding.etThresholdSvm.setSelection(formatted.length)
                isUpdatingSvmFromSlider = false
            }
        }

        binding.etThresholdSvm.doAfterTextChanged { text ->
            if (!isUpdatingSvmFromSlider) {
                val num = text.toString().toDoubleOrNull()
                if (num != null && num in 1.0..5.0) {
                    isUpdatingSvmFromText = true
                    binding.sliderSvm.value = num.toFloat()
                    isUpdatingSvmFromText = false
                }
            }
        }

        // 2. Roll Count Synchronization
        binding.sliderRollCount.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !isUpdatingRollFromText) {
                isUpdatingRollFromSlider = true
                val formatted = value.toInt().toString()
                binding.etThresholdRollCount.setText(formatted)
                binding.etThresholdRollCount.setSelection(formatted.length)
                isUpdatingRollFromSlider = false
            }
        }

        binding.etThresholdRollCount.doAfterTextChanged { text ->
            if (!isUpdatingRollFromSlider) {
                val num = text.toString().toIntOrNull()
                if (num != null && num in 1..10) {
                    isUpdatingRollFromText = true
                    binding.sliderRollCount.value = num.toFloat()
                    isUpdatingRollFromText = false
                }
            }
        }

        binding.toggleGroupSatuan.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            if (checkedId == R.id.btnMenit) {
                isMenit = true
                binding.sliderDurasiMiring.valueFrom = 1.0f
                binding.sliderDurasiMiring.valueTo = 150.0f // Maksimal 150 menit
                binding.sliderDurasiMiring.stepSize = 1.0f
                binding.sliderDurasiMiring.value = 1.0f

                binding.etDurasiMiringDetik.setText("1")
                binding.txtMenitDetik.suffixText = "mnt"
            } else {
                isMenit = false
                binding.sliderDurasiMiring.valueFrom = 1.0f
                binding.sliderDurasiMiring.valueTo = 60.0f  // Maksimal 60 detik
                binding.sliderDurasiMiring.stepSize = 1.0f
                binding.sliderDurasiMiring.value = 5.0f

                binding.etDurasiMiringDetik.setText("5")
                binding.txtMenitDetik.suffixText = "dtk"
            }
        }
        binding.sliderDurasiMiring.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !isUpdatingDurasiMiringFromText) {
                isUpdatingDurasiMiringFromSlider = true
                val formatted = value.toInt().toString()
                binding.etDurasiMiringDetik.setText(formatted)
                binding.etDurasiMiringDetik.setSelection(formatted.length)
                isUpdatingDurasiMiringFromSlider = false
            }
        }
        binding.etDurasiMiringDetik.doAfterTextChanged { text ->
            if (!isUpdatingDurasiMiringFromSlider) {
                val num = text.toString().toIntOrNull()
                val maxLimit = if (isMenit) 150 else 60 // 👈 Batas input menyesuaikan mode
                if (num != null && num in 1..maxLimit) {
                    isUpdatingDurasiMiringFromText = true
                    binding.sliderDurasiMiring.value = num.toFloat()
                    isUpdatingDurasiMiringFromText = false
                }
            }
        }

        // 2B. Durasi Miring Synchronization
        binding.sliderDurasiMiring.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !isUpdatingDurasiMiringFromText) {
                isUpdatingDurasiMiringFromSlider = true
                val formatted = value.toInt().toString()
                binding.etDurasiMiringDetik.setText(formatted)
                binding.etDurasiMiringDetik.setSelection(formatted.length)
                isUpdatingDurasiMiringFromSlider = false
            }
        }

        binding.etDurasiMiringDetik.doAfterTextChanged { text ->
            if (!isUpdatingDurasiMiringFromSlider) {
                val num = text.toString().toIntOrNull()
                if (num != null && num in 1..30) {
                    isUpdatingDurasiMiringFromText = true
                    binding.sliderDurasiMiring.value = num.toFloat()
                    isUpdatingDurasiMiringFromText = false
                }
            }
        }

        // 3. Countdown Duration Synchronization
        binding.sliderCountdown.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !isUpdatingCountFromText) {
                isUpdatingCountFromSlider = true
                val formatted = value.toInt().toString()
                binding.etCountdownDuration.setText(formatted)
                binding.etCountdownDuration.setSelection(formatted.length)
                isUpdatingCountFromSlider = false
            }
        }

        binding.etCountdownDuration.doAfterTextChanged { text ->
            if (!isUpdatingCountFromSlider) {
                val num = text.toString().toIntOrNull()
                if (num != null && num in 3..60) {
                    isUpdatingCountFromText = true
                    binding.sliderCountdown.value = num.toFloat()
                    isUpdatingCountFromText = false
                }
            }
        }

        // 4. Min Heart Rate Synchronization
        binding.sliderMinHeartRate.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !isUpdatingMinHrFromText) {
                isUpdatingMinHrFromSlider = true
                val formatted = value.toInt().toString()
                binding.etMinHeartRate.setText(formatted)
                binding.etMinHeartRate.setSelection(formatted.length)
                isUpdatingMinHrFromSlider = false
            }
        }

        binding.sliderMaxHeartRate.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !isUpdatingMaxHrFromText) {
                isUpdatingMaxHrFromSlider = true
                val formatted = value.toInt().toString()
                binding.etMaxHeartRate.setText(formatted)
                binding.etMaxHeartRate.setSelection(formatted.length)
                isUpdatingMaxHrFromSlider = false
            }
        }

        binding.etMinHeartRate.doAfterTextChanged { text ->
            if (!isUpdatingMinHrFromSlider) {
                val num = text.toString().toIntOrNull()
                if (num != null && num in 40..90) {
                    isUpdatingMinHrFromText = true
                    binding.sliderMinHeartRate.value = num.toFloat()
                    isUpdatingMinHrFromText = false
                }
            }
        }

        binding.etMaxHeartRate.doAfterTextChanged { text ->
            if (!isUpdatingMaxHrFromSlider) {
                val num = text.toString().toIntOrNull()
                if (num != null && num in 90..120) {
                    isUpdatingMaxHrFromText = true
                    binding.sliderMaxHeartRate.value = num.toFloat()
                    isUpdatingMaxHrFromText = false
                }
            }
        }

        // 5. Min SpO2 Synchronization
        binding.sliderMinSpo2.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !isUpdatingSpo2FromText) {
                isUpdatingSpo2FromSlider = true
                val formatted = value.toInt().toString()
                binding.etMinSpo2.setText(formatted)
                binding.etMinSpo2.setSelection(formatted.length)
                isUpdatingSpo2FromSlider = false
            }
        }

        binding.etMinSpo2.doAfterTextChanged { text ->
            if (!isUpdatingSpo2FromSlider) {
                val num = text.toString().toIntOrNull()
                if (num != null && num in 70..99) {
                    isUpdatingSpo2FromText = true
                    binding.sliderMinSpo2.value = num.toFloat()
                    isUpdatingSpo2FromText = false
                }
            }
        }

        // 6. Max Distance Synchronization
        binding.sliderMaxDistance.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !isUpdatingDistFromText) {
                isUpdatingDistFromSlider = true
                val formatted = value.toInt().toString()
                binding.etMaxDistance.setText(formatted)
                binding.etMaxDistance.setSelection(formatted.length)
                isUpdatingDistFromSlider = false
            }
        }

        binding.etMaxDistance.doAfterTextChanged { text ->
            if (!isUpdatingDistFromSlider) {
                val num = text.toString().toIntOrNull()
                if (num != null && num in 20..1000) {
                    isUpdatingDistFromText = true
                    binding.sliderMaxDistance.value = num.toFloat()
                    isUpdatingDistFromText = false
                }
            }
        }
    }

    private fun loadCurrentSettings() {
        val cachedSvm = sessionManager.getThresholdSvm()
        val cachedRoll = sessionManager.getThresholdRollCount()
        val cachedDurasiMiring = sessionManager.getDurasiMiringDetik()
        val cachedCount = sessionManager.getCountdownDuration()
        val cachedMinHr = sessionManager.getMinHeartRate()
        val cachedMaxHr = sessionManager.getMaxHeartRate()
        val cachedSpo2 = sessionManager.getMinSpo2()
        val cachedLoc = sessionManager.getLokasiDefaultNama()
        val cachedLat = sessionManager.getDefaultLatitude()
        val cachedLng = sessionManager.getDefaultLongitude()
        val cachedDist = sessionManager.getMaxDistanceMeters()

        applyValues(cachedSvm.toDouble(), cachedRoll, cachedDurasiMiring, cachedCount, cachedMinHr,cachedMaxHr, cachedSpo2, cachedLoc, cachedLat, cachedLng, cachedDist)

        val userId = sessionManager.getUserId()
        if (userId <= 0) return

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.getApiService(this@DeviceSettingsActivity).getPengaturanByUser(userId)
                }
                if (response.isSuccessful && response.body()?.success == true) {
                    val config = response.body()?.data
                    config?.let {
                        val svm = it.thresholdSvm ?: cachedSvm.toDouble()
                        val roll = it.thresholdRollCount ?: cachedRoll
                        val durasiMiring = it.durasiMiringDetik ?: cachedDurasiMiring
                        val count = it.durasiCountdown ?: cachedCount
                        val minhr = it.minHeartRate ?: cachedMinHr
                        val maxhr = it.maxHeartRate ?: cachedMaxHr
                        val spo2 = it.minSpo2 ?: cachedSpo2
                        val loc = it.lokasiDefaultNama ?: cachedLoc
                        val lat = it.defaultLatitude ?: cachedLat
                        val lng = it.defaultLongitude ?: cachedLng
                        val dist = it.maxDistanceMeters ?: cachedDist

                        applyValues(svm, roll, durasiMiring, count, minhr, maxhr, spo2, loc, lat, lng, dist)
                        sessionManager.saveDeviceConfig(
                            svm = svm.toFloat(),
                            rollCount = roll,
                            durasiMiringDetik = durasiMiring,
                            countdown = count,
                            minHr = minhr,
                            maxHr = maxhr,
                            minSpo2 = spo2,
                            locName = loc,
                            defaultLat = lat,
                            defaultLng = lng,
                            maxDistMeters = dist
                        )
                    }
                }
            } catch (e: Exception) {
                // Keep cached values
            }
        }
    }

    private fun applyValues(
        svm: Double, 
        roll: Int, 
        durasiMiring: Int,
        count: Int, 
        minHr: Int,
        maxHr : Int,
        minSpo2: Int, 
        locName: String, 
        defaultLat: Double, 
        defaultLng: Double, 
        maxDist: Int
    ) {
        val clampedSvm = svm.coerceIn(1.0, 5.0).toFloat()
        val clampedRoll = roll.coerceIn(1, 10).toFloat()
        val clampedDurasiMiring = durasiMiring.coerceIn(1, 30).toFloat()
        val clampedCount = count.coerceIn(3, 60).toFloat()
        val clampedMinHr = minHr.coerceIn(40, 90).toFloat()
        val clampedMaxHr = maxHr.coerceIn(90, 150).toFloat()
        val clampedSpo2 = minSpo2.coerceIn(70, 99).toFloat()
        val clampedDist = maxDist.coerceIn(20, 1000).toFloat()

        isUpdatingSvmFromSlider = true
        binding.sliderSvm.value = clampedSvm
        binding.etThresholdSvm.setText(String.format(Locale.US, "%.2f", clampedSvm))
        isUpdatingSvmFromSlider = false

        isUpdatingRollFromSlider = true
        binding.sliderRollCount.value = clampedRoll
        binding.etThresholdRollCount.setText(clampedRoll.toInt().toString())
        isUpdatingRollFromSlider = false

        isUpdatingDurasiMiringFromSlider = true
        binding.sliderDurasiMiring.value = clampedDurasiMiring
        binding.etDurasiMiringDetik.setText(clampedDurasiMiring.toInt().toString())
        isUpdatingDurasiMiringFromSlider = false

        isUpdatingCountFromSlider = true
        binding.sliderCountdown.value = clampedCount
        binding.etCountdownDuration.setText(clampedCount.toInt().toString())
        isUpdatingCountFromSlider = false

        isUpdatingMinHrFromSlider = true
        binding.sliderMinHeartRate.value = clampedMinHr
        binding.etMinHeartRate.setText(clampedMinHr.toInt().toString())
        isUpdatingMinHrFromSlider = false

        isUpdatingMaxHrFromSlider = true
        binding.sliderMaxHeartRate.value = clampedMaxHr
        binding.etMaxHeartRate.setText(clampedMaxHr.toInt().toString())
        isUpdatingMaxHrFromSlider = false

        isUpdatingSpo2FromSlider = true
        binding.sliderMinSpo2.value = clampedSpo2
        binding.etMinSpo2.setText(clampedSpo2.toInt().toString())
        isUpdatingSpo2FromSlider = false

        binding.etLokasiDefaultNama.setText(locName)
        binding.etDefaultLatitude.setText(String.format(Locale.US, "%.6f", defaultLat))
        binding.etDefaultLongitude.setText(String.format(Locale.US, "%.6f", defaultLng))

        isUpdatingDistFromSlider = true
        binding.sliderMaxDistance.value = clampedDist
        binding.etMaxDistance.setText(clampedDist.toInt().toString())
        isUpdatingDistFromSlider = false
    }

    private fun saveSettings() {
        val svmStr = binding.etThresholdSvm.text.toString().trim()
        val rollStr = binding.etThresholdRollCount.text.toString().trim()
        val durasiMiringStr = binding.etDurasiMiringDetik.text.toString().trim()
        val countdownStr = binding.etCountdownDuration.text.toString().trim()
        val hrStr = binding.etMinHeartRate.text.toString().trim()
        val spo2Str = binding.etMinSpo2.text.toString().trim()
        val locName = binding.etLokasiDefaultNama.text.toString().trim()
        val latStr = binding.etDefaultLatitude.text.toString().trim()
        val lngStr = binding.etDefaultLongitude.text.toString().trim()
        val distStr = binding.etMaxDistance.text.toString().trim()

        val svm = svmStr.toDoubleOrNull() ?: binding.sliderSvm.value.toDouble()
        val roll = rollStr.toIntOrNull() ?: binding.sliderRollCount.value.toInt()
        val durasiMiringInput = durasiMiringStr.toIntOrNull() ?: binding.sliderDurasiMiring.value.toInt()
        val durasiMiring = if (isMenit) durasiMiringInput * 60 else durasiMiringInput
        val countdown = countdownStr.toIntOrNull() ?: binding.sliderCountdown.value.toInt()
        val hr = hrStr.toIntOrNull() ?: binding.sliderMinHeartRate.value.toInt()
        val spo2 = spo2Str.toIntOrNull() ?: binding.sliderMinSpo2.value.toInt()
        val lat = latStr.toDoubleOrNull() ?: -3.695436
        val lng = lngStr.toDoubleOrNull() ?: 128.181427
        val maxDist = distStr.toIntOrNull() ?: binding.sliderMaxDistance.value.toInt()

        if (svm < 1.0 || svm > 10.0) {
            binding.etThresholdSvm.error = "Batas SVM harus antara 1.0 - 10.0"
            return
        }
        if (roll < 1 || roll > 50) {
            binding.etThresholdRollCount.error = "Batas miring harus antara 1 - 50"
            return
        }
        if (isMenit) {
            if (durasiMiringInput < 1 || durasiMiringInput > 150) {
                binding.etDurasiMiringDetik.error = "Durasi miring harus antara 1 - 150 menit"
                return
            }
        } else {
            if (durasiMiringInput < 1 || durasiMiringInput > 60) {
                binding.etDurasiMiringDetik.error = "Durasi miring harus antara 1 - 60 detik"
                return
            }
        }
        if (countdown < 3 || countdown > 120) {
            binding.etCountdownDuration.error = "Durasi countdown harus antara 3 - 120 detik"
            return
        }
        if (hr < 40 || hr > 100) {
            binding.etMinHeartRate.error = "Batas detak jantung harus antara 40 - 100 BPM"
            return
        }
        if (spo2 < 60 || spo2 > 99) {
            binding.etMinSpo2.error = "Batas SpO2 harus antara 60 - 99 %"
            return
        }
        if (maxDist < 10 || maxDist > 5000) {
            binding.etMaxDistance.error = "Batas radius jarak harus antara 10 - 5000 meter"
            return
        }

        binding.btnSaveSettings.isEnabled = false
        binding.btnSaveSettings.text = "Menyimpan ke Perangkat..."

        val userId = sessionManager.getUserId()
        val req = UpdatePengaturanRequest(
            thresholdSvm = svm,
            thresholdRollCount = roll,
            durasiMiringDetik = durasiMiring,
            durasiCountdown = countdown,
            minHeartRate = hr,
            minSpo2 = spo2,
            lokasiDefaultNama = if (locName.isNotEmpty()) locName else "Wisma Panti INAKAKA Ambon",
            defaultLatitude = lat,
            defaultLongitude = lng,
            maxDistanceMeters = maxDist
        )

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.getApiService(this@DeviceSettingsActivity).updatePengaturanByUser(userId, req)
                }
                if (response.isSuccessful && response.body()?.success == true) {
                    sessionManager.saveDeviceConfig(
                        svm = svm.toFloat(),
                        rollCount = roll,
                        durasiMiringDetik = durasiMiring,
                        countdown = countdown,
                        minHr = hr,
                        minSpo2 = spo2,
                        locName = if (locName.isNotEmpty()) locName else "Wisma Panti INAKAKA Ambon",
                        defaultLat = lat,
                        defaultLng = lng,
                        maxDistMeters = maxDist
                    )
                    Toast.makeText(
                        this@DeviceSettingsActivity,
                        "✅ Pengaturan Sabuk IoT berhasil disimpan!\nPico W & sistem akan otomatis memperbarui.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                } else {
                    val rawErr = response.errorBody()?.string()
                    var errorMsg: String? = null
                    if (!rawErr.isNullOrEmpty()) {
                        try {
                            val json = org.json.JSONObject(rawErr)
                            if (json.has("message") && !json.isNull("message")) {
                                errorMsg = json.getString("message").trim()
                            }
                        } catch (e: Exception) {
                            errorMsg = rawErr.take(150)
                        }
                    }
                    if (errorMsg.isNullOrEmpty()) {
                        val respMsg = response.message().trim()
                        errorMsg = if (respMsg.isNotEmpty()) respMsg else "Kode HTTP ${response.code()}"
                    }
                    Toast.makeText(this@DeviceSettingsActivity, "Gagal menyimpan: $errorMsg", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DeviceSettingsActivity, "Error koneksi: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnSaveSettings.isEnabled = true
                binding.btnSaveSettings.text = "SIMPAN PENGATURAN SABUK"
            }
        }
    }
}
