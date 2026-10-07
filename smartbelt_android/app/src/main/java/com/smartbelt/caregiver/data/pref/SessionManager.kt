package com.smartbelt.caregiver.data.pref

//Menyimpan data login (seperti Token Rahasia, ID Perawat, Nama, dan Role di memori HP).
//Mengingat status login (supaya saat aplikasi dibuka lagi, perawat tidak perlu login ulang).

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "smartbelt_caregiver_pref"
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_EMAIL = "email"
        private const val KEY_NAMA_LENGKAP = "nama_lengkap"
        private const val KEY_ROLE = "role"
        private const val KEY_DARK_MODE = "dark_mode"

        // IP Server Backend
        const val DEFAULT_BASE_URL = "http://YOUR_SERVER_IP:8088/" // use your own ip
    }

    fun saveAuthToken(token: String, idUser: Int, username: String, email: String, nama: String, role: String) {
        prefs.edit().apply {
            putString(KEY_TOKEN, token)
            putInt(KEY_USER_ID, idUser)
            putString(KEY_USERNAME, username)
            putString(KEY_EMAIL, email)
            putString(KEY_NAMA_LENGKAP, nama)
            putString(KEY_ROLE, role)
            apply()
        }
    }

    fun getAuthToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun isLoggedIn(): Boolean = !getAuthToken().isNullOrEmpty()

    fun getUserId(): Int = prefs.getInt(KEY_USER_ID, 0)
    fun getUsername(): String? = prefs.getString(KEY_USERNAME, "")
    fun getEmail(): String? = prefs.getString(KEY_EMAIL, "")
    fun getNamaLengkap(): String? = prefs.getString(KEY_NAMA_LENGKAP, "Caregiver")
    fun getRole(): String? = prefs.getString(KEY_ROLE, "CAREGIVER")

    fun updateUserSession(nama: String, username: String, email: String) {
        prefs.edit().apply {
            putString(KEY_NAMA_LENGKAP, nama)
            putString(KEY_USERNAME, username)
            putString(KEY_EMAIL, email)
            apply()
        }
    }

    fun getBaseUrl(): String = DEFAULT_BASE_URL

    // Dark Mode
    fun isDarkMode(): Boolean = prefs.getBoolean(KEY_DARK_MODE, false)
    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    // Dynamic Device Settings Cache
    fun saveDeviceConfig(
        svm: Float, 
        rollCount: Int,
        durasiMiringDetik: Int = 5,
        countdown: Int,
        minHr: Int = 60,
        maxHr: Int = 120,
        minSpo2: Int = 95,
        locName: String = "Rumah (UBAYA)",
        defaultLat: Double = -7.3198009,
        defaultLng: Double = 112.7681119,
        maxDistMeters: Int = 100
    ) {
        prefs.edit().apply {
            putFloat("threshold_svm", svm)
            putInt("threshold_roll_count", rollCount)
            putInt("durasi_miring_detik", durasiMiringDetik)
            putInt("durasi_countdown", countdown)
            putInt("min_heart_rate", minHr)
            putInt("max_heart_rate", maxHr)
            putInt("min_spo2", minSpo2)
            putString("lokasi_default_nama", locName)
            putString("default_latitude", defaultLat.toString())
            putString("default_longitude", defaultLng.toString())
            putInt("max_distance_meters", maxDistMeters)
            apply()
        }
    }

    fun getThresholdSvm(): Float = prefs.getFloat("threshold_svm", 2.70f)
    fun getThresholdRollCount(): Int = prefs.getInt("threshold_roll_count", 5)
    fun getDurasiMiringDetik(): Int = prefs.getInt("durasi_miring_detik", 5)
    fun getCountdownDuration(): Int = prefs.getInt("durasi_countdown", 30)
    fun getMinHeartRate(): Int = prefs.getInt("min_heart_rate", 60)
    fun getMaxHeartRate(): Int = prefs.getInt("max_heart_rate", 120)

    fun getMinSpo2(): Int = prefs.getInt("min_spo2", 95)
    fun getLokasiDefaultNama(): String {
        val n = prefs.getString("lokasi_default_nama", "Rumah (UBAYA)") ?: "Rumah (UBAYA)"
        return if (n.contains("INAKAKA") || n.contains("Ambon")) "Rumah (UBAYA)" else n
    }
    fun getDefaultLatitude(): Double {
        val v = prefs.getString("default_latitude", "-7.3198009")?.toDoubleOrNull() ?: -7.3198009
        return if (v == -3.695436) -7.3198009 else v
    }
    fun getDefaultLongitude(): Double {
        val v = prefs.getString("default_longitude", "112.7681119")?.toDoubleOrNull() ?: 112.7681119
        return if (v == 128.181427) 112.7681119 else v
    }
    fun getMaxDistanceMeters(): Int = prefs.getInt("max_distance_meters", 100)

    fun clearSession() {
        val darkMode = isDarkMode()
        prefs.edit().clear().apply()
        setDarkMode(darkMode) // Pertahankan preferensi dark mode
    }
}

