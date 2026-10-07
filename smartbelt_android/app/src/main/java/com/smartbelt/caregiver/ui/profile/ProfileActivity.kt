package com.smartbelt.caregiver.ui.profile

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smartbelt.caregiver.data.api.ApiClient
import com.smartbelt.caregiver.data.pref.SessionManager
import com.smartbelt.caregiver.databinding.ActivityProfileBinding
import com.smartbelt.caregiver.ui.auth.LoginActivity
import com.smartbelt.caregiver.ui.settings.SettingsMainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        binding.btnBack.setOnClickListener { finish() }

        // Setup default text from session
        binding.tvProfileName.text = sessionManager.getNamaLengkap()
        binding.tvProfileRole.text = "${sessionManager.getRole()} • WISMA INAKAKA"
        binding.tvSummaryUsername.text = sessionManager.getUsername()
        binding.tvSummaryEmail.text = sessionManager.getEmail()

        // Card Navigasi ke Pengaturan Sistem Utama
        binding.cardSettingsMenu.setOnClickListener {
            val intent = Intent(this, SettingsMainActivity::class.java)
            startActivity(intent)
        }

        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Konfirmasi Logout")
                .setMessage("Apakah Anda yakin ingin keluar dari akun?")
                .setPositiveButton("Ya, Keluar") { _, _ ->
                    sessionManager.clearSession()
                    ApiClient.resetClient()
                    try {
                        com.smartbelt.caregiver.service.EmergencyMonitoringService.stopService(this)
                    } catch (e: Exception) {}
                    val intent = Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        loadUserProfile()
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when returning from settings / account edit
        loadUserProfile()
    }

    private fun loadUserProfile() {
        val userId = sessionManager.getUserId()
        if (userId <= 0) return

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.getApiService(this@ProfileActivity).getUserById(userId)
                }
                if (response.isSuccessful && response.body()?.success == true) {
                    val user = response.body()?.data
                    user?.let {
                        val nama = it.namaLengkap ?: sessionManager.getNamaLengkap()
                        val username = it.username ?: sessionManager.getUsername()
                        val email = it.email ?: sessionManager.getEmail()
                        val phone = it.noTelepon ?: "-"
                        val telegram = it.telegramChatId ?: "-"

                        binding.tvProfileName.text = nama
                        binding.tvSummaryUsername.text = username
                        binding.tvSummaryEmail.text = email
                        binding.tvSummaryPhone.text = phone
                        binding.tvSummaryTelegram.text = telegram
                    }
                }
            } catch (e: Exception) {
                // Keep values from session cache
            }
        }
    }
}
