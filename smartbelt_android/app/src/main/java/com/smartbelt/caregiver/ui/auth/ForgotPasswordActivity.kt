package com.smartbelt.caregiver.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smartbelt.caregiver.data.api.ApiClient
import com.smartbelt.caregiver.data.model.ForgotPasswordRequest
import com.smartbelt.caregiver.databinding.ActivityForgotPasswordBinding
import kotlinx.coroutines.launch

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding
    private var usernameOrEmail: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        usernameOrEmail = intent.getStringExtra("USERNAME_OR_EMAIL") ?: ""
        val infoMsg = intent.getStringExtra("INFO_MESSAGE")

        if (!infoMsg.isNullOrEmpty()) {
            binding.tvInfoSubtitle.text = infoMsg
        } else if (usernameOrEmail.isNotEmpty()) {
            binding.tvInfoSubtitle.text = "Kode verifikasi 8 karakter telah dikirimkan ke email terdaftar untuk akun '${usernameOrEmail}'. Silakan periksa kotak masuk/spam Gmail Anda:"
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnVerifikasiToken.setOnClickListener {
            val token = binding.etToken.text.toString().trim().replace(" ", "").uppercase(java.util.Locale.ROOT)
            if (token.isEmpty()) {
                binding.tilToken.error = "Kode verifikasi tidak boleh kosong!"
                return@setOnClickListener
            }
            if (token.length < 6) {
                binding.tilToken.error = "Kode verifikasi minimal 6 karakter!"
                return@setOnClickListener
            }
            binding.tilToken.error = null

            // Validasi ke server backend terlebih dahulu!
            binding.progressBar.visibility = View.VISIBLE
            binding.btnVerifikasiToken.isEnabled = false

            lifecycleScope.launch {
                try {
                    val api = ApiClient.getApiService(this@ForgotPasswordActivity)
                    val res = api.verifyResetToken(mapOf("token" to token))

                    binding.progressBar.visibility = View.GONE
                    binding.btnVerifikasiToken.isEnabled = true

                    if (res.isSuccessful && res.body()?.success == true) {
                        Toast.makeText(this@ForgotPasswordActivity, "Kode verifikasi valid!", Toast.LENGTH_SHORT).show()

                        // Token BENAR -> Lanjut ke halaman Buat Kata Sandi Baru
                        val intent = Intent(this@ForgotPasswordActivity, ResetPasswordActivity::class.java).apply {
                            putExtra("TOKEN", token)
                            putExtra("USERNAME_OR_EMAIL", usernameOrEmail)
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        val errMsg = res.body()?.message ?: "Kode verifikasi salah atau kedaluwarsa!"
                        binding.tilToken.error = errMsg
                        Toast.makeText(this@ForgotPasswordActivity, "❌ $errMsg", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnVerifikasiToken.isEnabled = true
                    Toast.makeText(this@ForgotPasswordActivity, "Gagal memverifikasi: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.tvKirimUlang.setOnClickListener {
            if (usernameOrEmail.isEmpty()) {
                Toast.makeText(this, "Username tidak ditemukan", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.progressBar.visibility = View.VISIBLE
            binding.btnVerifikasiToken.isEnabled = false

            lifecycleScope.launch {
                try {
                    val api = ApiClient.getApiService(this@ForgotPasswordActivity)
                    val res = api.forgotPassword(ForgotPasswordRequest(usernameOrEmail))

                    binding.progressBar.visibility = View.GONE
                    binding.btnVerifikasiToken.isEnabled = true

                    if (res.isSuccessful && res.body()?.success == true) {
                        Toast.makeText(this@ForgotPasswordActivity, "Kode baru berhasil dikirim ke email!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@ForgotPasswordActivity, res.body()?.message ?: "Gagal mengirim ulang kode", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnVerifikasiToken.isEnabled = true
                    Toast.makeText(this@ForgotPasswordActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
