package com.smartbelt.caregiver.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smartbelt.caregiver.data.api.ApiClient
import com.smartbelt.caregiver.data.model.ResetPasswordRequest
import com.smartbelt.caregiver.databinding.ActivityResetPasswordBinding
import kotlinx.coroutines.launch

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResetPasswordBinding
    private var token: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        token = intent.getStringExtra("TOKEN") ?: ""

        if (token.isEmpty()) {
            Toast.makeText(this, "Token verifikasi tidak valid!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnResetSubmit.setOnClickListener {
            val newPass = binding.etNewPassword.text.toString().trim()
            val confirmPass = binding.etConfirmPassword.text.toString().trim()

            if (newPass.length < 6) {
                binding.tilNewPassword.error = "Kata sandi baru minimal 6 karakter!"
                return@setOnClickListener
            }
            binding.tilNewPassword.error = null

            if (newPass != confirmPass) {
                binding.tilConfirmPassword.error = "Konfirmasi kata sandi tidak cocok!"
                return@setOnClickListener
            }
            binding.tilConfirmPassword.error = null

            binding.progressBar.visibility = View.VISIBLE
            binding.btnResetSubmit.isEnabled = false

            lifecycleScope.launch {
                try {
                    val api = ApiClient.getApiService(this@ResetPasswordActivity)
                    val res = api.resetPassword(ResetPasswordRequest(token, newPass))

                    binding.progressBar.visibility = View.GONE
                    binding.btnResetSubmit.isEnabled = true

                    if (res.isSuccessful && res.body()?.success == true) {
                        Toast.makeText(this@ResetPasswordActivity, "Kata sandi berhasil diperbarui! Silakan masuk dengan kata sandi baru.", Toast.LENGTH_LONG).show()
                        finish() // Kembali ke halaman Login
                    } else {
                        Toast.makeText(this@ResetPasswordActivity, res.body()?.message ?: "Gagal mereset kata sandi", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnResetSubmit.isEnabled = true
                    Toast.makeText(this@ResetPasswordActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
