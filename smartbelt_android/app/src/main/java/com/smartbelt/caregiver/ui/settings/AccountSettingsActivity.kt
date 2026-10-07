package com.smartbelt.caregiver.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smartbelt.caregiver.data.api.ApiClient
import com.smartbelt.caregiver.data.model.User
import com.smartbelt.caregiver.data.pref.SessionManager
import com.smartbelt.caregiver.databinding.ActivityAccountSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccountSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountSettingsBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        binding.btnBack.setOnClickListener { finish() }

        // Setup cached values from session
        binding.etNamaLengkap.setText(sessionManager.getNamaLengkap())
        binding.etUsername.setText(sessionManager.getUsername())
        binding.etEmail.setText(sessionManager.getEmail())

        loadUserProfile()

        binding.btnSaveAccount.setOnClickListener {
            saveAccountInfo()
        }
    }

    private fun loadUserProfile() {
        val userId = sessionManager.getUserId()
        if (userId <= 0) return

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.getApiService(this@AccountSettingsActivity).getUserById(userId)
                }
                if (response.isSuccessful && response.body()?.success == true) {
                    val user = response.body()?.data
                    user?.let {
                        binding.etNamaLengkap.setText(it.namaLengkap ?: sessionManager.getNamaLengkap())
                        binding.etUsername.setText(it.username ?: sessionManager.getUsername())
                        binding.etEmail.setText(it.email ?: sessionManager.getEmail())
                        binding.etNoTelepon.setText(it.noTelepon ?: "")
                        binding.etTelegramChatId.setText(it.telegramChatId ?: "")
                    }
                }
            } catch (e: Exception) {
                // Keep values from session cache
            }
        }
    }

    private fun saveAccountInfo() {
        val nama = binding.etNamaLengkap.text.toString().trim()
        val username = binding.etUsername.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val noTelepon = binding.etNoTelepon.text.toString().trim()
        val telegramChatId = binding.etTelegramChatId.text.toString().trim()

        if (nama.isEmpty()) {
            binding.etNamaLengkap.error = "Nama lengkap harus diisi"
            return
        }
        if (username.isEmpty()) {
            binding.etUsername.error = "Username harus diisi"
            return
        }
        if (email.isEmpty()) {
            binding.etEmail.error = "Email harus diisi"
            return
        }

        binding.btnSaveAccount.isEnabled = false
        binding.btnSaveAccount.text = "Menyimpan..."

        val userId = sessionManager.getUserId()
        val userUpdate = User(
            idUser = userId,
            namaLengkap = nama,
            username = username,
            email = email,
            noTelepon = noTelepon.ifEmpty { null },
            telegramChatId = telegramChatId.ifEmpty { null }
        )

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.getApiService(this@AccountSettingsActivity).updateUser(userId, userUpdate)
                }
                if (response.isSuccessful && response.body()?.success == true) {
                    sessionManager.updateUserSession(nama, username, email)
                    Toast.makeText(this@AccountSettingsActivity, "✅ Informasi akun berhasil diperbarui!", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    val errorMsg = response.body()?.message ?: response.message()
                    Toast.makeText(this@AccountSettingsActivity, "Gagal memperbarui: $errorMsg", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AccountSettingsActivity, "Error koneksi: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnSaveAccount.isEnabled = true
                binding.btnSaveAccount.text = "SIMPAN INFORMASI AKUN"
            }
        }
    }
}
