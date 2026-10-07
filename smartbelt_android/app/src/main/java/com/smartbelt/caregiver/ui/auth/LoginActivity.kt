package com.smartbelt.caregiver.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smartbelt.caregiver.data.api.ApiClient
import com.smartbelt.caregiver.data.model.ForgotPasswordRequest
import com.smartbelt.caregiver.data.model.LoginRequest
import com.smartbelt.caregiver.data.pref.SessionManager
import com.smartbelt.caregiver.databinding.ActivityLoginBinding
import com.smartbelt.caregiver.ui.main.MainActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (username.isEmpty()) {
                binding.tilUsername.error = "Username/Email tidak boleh kosong"
                return@setOnClickListener
            }
            binding.tilUsername.error = null

            if (password.isEmpty()) {
                binding.tilPassword.error = "Password tidak boleh kosong"
                return@setOnClickListener
            }
            binding.tilPassword.error = null

            performLogin(username, password)
        }

        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.tvForgotPassword.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            if (username.isEmpty()) {
                binding.tilUsername.error = "Masukkan username atau email terlebih dahulu!"
                Toast.makeText(this, "Silakan isi Username atau Email terlebih dahulu!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.tilUsername.error = null

            // Kirim kode verifikasi langsung via email berdasarkan username
            binding.progressBar.visibility = View.VISIBLE
            binding.btnLogin.isEnabled = false

            lifecycleScope.launch {
                try {
                    val api = ApiClient.getApiService(this@LoginActivity)
                    val res = api.forgotPassword(ForgotPasswordRequest(username))

                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true

                    if (res.isSuccessful && res.body()?.success == true) {
                        Toast.makeText(this@LoginActivity, res.body()?.message ?: "Kode verifikasi telah dikirim ke email!", Toast.LENGTH_LONG).show()

                        val intent = Intent(this@LoginActivity, ForgotPasswordActivity::class.java).apply {
                            putExtra("USERNAME_OR_EMAIL", username)
                            putExtra("INFO_MESSAGE", res.body()?.message)
                        }
                        startActivity(intent)
                    } else {
                        Toast.makeText(this@LoginActivity, res.body()?.message ?: "Gagal memproses permintaan reset", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    Toast.makeText(this@LoginActivity, "Koneksi gagal: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun performLogin(usernameOrEmail: String, pass: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false

        lifecycleScope.launch {
            try {
                val apiService = ApiClient.getApiService(this@LoginActivity)
                val response = apiService.login(LoginRequest(usernameOrEmail, pass))

                binding.progressBar.visibility = View.GONE
                binding.btnLogin.isEnabled = true

                if (response.isSuccessful && response.body()?.success == true) {
                    val authData = response.body()?.data
                    if (authData != null && !authData.token.isNullOrEmpty()) {
                        sessionManager.saveAuthToken(
                            token = authData.token,
                            idUser = authData.idUser ?: 0,
                            username = authData.username ?: "",
                            email = authData.email ?: "",
                            nama = authData.namaLengkap ?: "Caregiver",
                            role = authData.role ?: "CAREGIVER"
                        )

                        Toast.makeText(this@LoginActivity, "Selamat datang, ${authData.namaLengkap}!", Toast.LENGTH_SHORT).show()
                        try {
                            com.smartbelt.caregiver.service.EmergencyMonitoringService.startService(this@LoginActivity)
                        } catch (e: Exception) {}
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    }
                } else {
                    val rawError = try { response.errorBody()?.string() } catch (e: Exception) { null }
                    val errorMsg = try {
                        val json = org.json.JSONObject(rawError ?: "")
                        json.optString("message", response.body()?.message ?: "Login gagal! Periksa username/password.")
                    } catch (e: Exception) {
                        response.body()?.message ?: "Login gagal! Periksa username/password."
                    }

                    if (response.code() == 403 || errorMsg.contains("persetujuan", ignoreCase = true) || errorMsg.contains("approval", ignoreCase = true)) {
                        androidx.appcompat.app.AlertDialog.Builder(this@LoginActivity)
                            .setTitle("Akun Menunggu Persetujuan ⏳")
                            .setMessage(errorMsg)
                            .setPositiveButton("Tutup", null)
                            .show()
                    } else {
                        Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnLogin.isEnabled = true
                Toast.makeText(this@LoginActivity, "Koneksi gagal: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
