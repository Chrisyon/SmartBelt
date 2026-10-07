package com.smartbelt.caregiver.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smartbelt.caregiver.data.api.ApiClient
import com.smartbelt.caregiver.data.model.RegisterRequest
import com.smartbelt.caregiver.databinding.ActivityRegisterBinding
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    private var selectedRole: String = "KELUARGA"

    private val rolesDisplay = listOf(
        "Keluarga (Anggota Keluarga)",
        "Pengguna Sabuk (Lansia)",
        "Caregiver (Perawat Wisma)"
    )

    private val roleValues = listOf(
        "KELUARGA",
        "PENGGUNA_BELT",
        "CAREGIVER"
    )

    private var listPenghuni: List<com.smartbelt.caregiver.data.model.Penghuni> = emptyList()
    private var selectedPenghuniId: Int? = null

    private val hubunganList = listOf("Anak", "Cucu", "Pasangan", "Menantu", "Saudara", "Kerabat")
    private val genderList = listOf("Laki-laki", "Perempuan")

    private fun resolveRole(text: String): String {
        val trimmed = text.trim()
        return when {
            trimmed.startsWith("Pengguna", ignoreCase = true) || trimmed.equals("PENGGUNA_BELT", ignoreCase = true) -> "PENGGUNA_BELT"
            trimmed.startsWith("Caregiver", ignoreCase = true) || trimmed.startsWith("Perawat", ignoreCase = true) || trimmed.equals("CAREGIVER", ignoreCase = true) -> "CAREGIVER"
            trimmed.startsWith("Keluarga", ignoreCase = true) || trimmed.equals("KELUARGA", ignoreCase = true) -> "KELUARGA"
            else -> selectedRole
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        // Setup Role Dropdown
        val roleAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, rolesDisplay)
        binding.actvRole.setAdapter(roleAdapter)
        binding.actvRole.setText(rolesDisplay[0], false)
        selectedRole = roleValues[0]

        updateRoleSectionVisibility(selectedRole)

        binding.actvRole.setOnItemClickListener { _, _, position, _ ->
            if (position in roleValues.indices) {
                selectedRole = roleValues[position]
                updateRoleSectionVisibility(selectedRole)
            }
        }

        // Setup Hubungan Keluarga
        val hubAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, hubunganList)
        binding.actvHubunganKeluarga.setAdapter(hubAdapter)
        binding.actvHubunganKeluarga.setText(hubunganList[0], false)

        // Setup Jenis Kelamin
        val genderAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genderList)
        binding.actvJenisKelamin.setAdapter(genderAdapter)
        binding.actvJenisKelamin.setText(genderList[0], false)

        // Setup DatePicker untuk Lansia
        binding.etTanggalLahir.setOnClickListener {
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.YEAR, -65) // Default usia lansia ~65 th
            val dpd = android.app.DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val dateStr = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                    binding.etTanggalLahir.setText(dateStr)
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            )
            dpd.show()
        }

        fetchPenghuniList()

        binding.btnRegister.setOnClickListener {
            val nama = binding.etNamaLengkap.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val username = binding.etUsername.text.toString().trim()
            val phone = binding.etNoTelepon.text.toString().trim()
            val telegramChatId = binding.etTelegramChatId.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            val roleToRegister = resolveRole(binding.actvRole.text.toString())

            if (nama.isEmpty() || email.isEmpty() || username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Harap isi semua kolom bertanda wajib!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                binding.tilPassword.error = "Password minimal 6 karakter"
                return@setOnClickListener
            }
            binding.tilPassword.error = null

            // Validasi khusus role Keluarga
            var idPenghuniTarget: Int? = null
            var hubunganKeluarga: String? = null
            if (roleToRegister == "KELUARGA") {
                if (listPenghuni.isNotEmpty() && selectedPenghuniId == null) {
                    Toast.makeText(this, "Silakan pilih lansia penghuni yang Anda dampingi!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                idPenghuniTarget = selectedPenghuniId
                hubunganKeluarga = binding.actvHubunganKeluarga.text.toString().trim().ifEmpty { "Keluarga" }
            }

            // Validasi khusus role Pengguna Sabuk (Lansia)
            var tanggalLahir: String? = null
            var jenisKelamin: String? = null
            var alamatKamar: String? = null
            var riwayatPenyakit: String? = null
            if (roleToRegister == "PENGGUNA_BELT") {
                alamatKamar = binding.etAlamatKamar.text.toString().trim()
                if (alamatKamar.isEmpty()) {
                    binding.tilAlamatKamar.error = "Nomor / nama kamar wajib diisi untuk lansia"
                    binding.etAlamatKamar.requestFocus()
                    return@setOnClickListener
                }
                binding.tilAlamatKamar.error = null

                tanggalLahir = binding.etTanggalLahir.text.toString().trim().ifEmpty { null }
                val genderText = binding.actvJenisKelamin.text.toString()
                jenisKelamin = if (genderText.contains("Perempuan", ignoreCase = true)) "P" else "L"
                riwayatPenyakit = binding.etRiwayatPenyakit.text.toString().trim().ifEmpty { null }
            }

            binding.progressBar.visibility = View.VISIBLE
            binding.btnRegister.isEnabled = false

            lifecycleScope.launch {
                try {
                    val apiService = ApiClient.getApiService(this@RegisterActivity)
                    val req = RegisterRequest(
                        namaLengkap = nama,
                        email = email,
                        username = username,
                        password = password,
                        noTelepon = phone.ifEmpty { null },
                        telegramChatId = telegramChatId.ifEmpty { null },
                        role = roleToRegister,
                        idPenghuniTarget = idPenghuniTarget,
                        hubunganKeluarga = hubunganKeluarga,
                        tanggalLahir = tanggalLahir,
                        jenisKelamin = jenisKelamin,
                        alamatKamar = alamatKamar,
                        riwayatPenyakit = riwayatPenyakit
                    )

                    val response = apiService.register(req)
                    binding.progressBar.visibility = View.GONE
                    binding.btnRegister.isEnabled = true

                    if (response.isSuccessful && response.body()?.success == true) {
                        val roleLabel = when (roleToRegister) {
                            "PENGGUNA_BELT" -> "Pengguna Sabuk (Lansia)"
                            "CAREGIVER" -> "Caregiver (Perawat Wisma)"
                            else -> "Keluarga Lansia"
                        }
                        AlertDialog.Builder(this@RegisterActivity)
                            .setTitle("Pendaftaran Berhasil! 🎉")
                            .setMessage("Akun Anda telah berhasil didaftarkan sebagai $roleLabel.\n\nSaat ini akun Anda berstatus NONAKTIF dan sedang menunggu persetujuan (approval) oleh Admin Wisma INAKAKA sebelum dapat digunakan untuk masuk.")
                            .setPositiveButton("Mengerti") { _, _ -> finish() }
                            .setCancelable(false)
                            .show()
                    } else {
                        val msg = response.body()?.message ?: "Registrasi gagal!"
                        Toast.makeText(this@RegisterActivity, msg, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnRegister.isEnabled = true
                    Toast.makeText(this@RegisterActivity, "Kesalahan: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.tvLogin.setOnClickListener {
            finish()
        }
    }

    private fun updateRoleSectionVisibility(role: String) {
        when (role) {
            "KELUARGA" -> {
                binding.cardKeluargaLink.visibility = View.VISIBLE
                binding.cardLansiaExtra.visibility = View.GONE
            }
            "PENGGUNA_BELT" -> {
                binding.cardKeluargaLink.visibility = View.GONE
                binding.cardLansiaExtra.visibility = View.VISIBLE
            }
            else -> {
                binding.cardKeluargaLink.visibility = View.GONE
                binding.cardLansiaExtra.visibility = View.GONE
            }
        }
    }

    private fun fetchPenghuniList() {
        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(this@RegisterActivity)
                val res = api.getAllPenghuni()
                if (res.isSuccessful && res.body()?.success == true) {
                    listPenghuni = res.body()?.data ?: emptyList()
                    setupLansiaDropdown(listPenghuni)
                } else {
                    setupLansiaDropdown(emptyList())
                }
            } catch (e: Exception) {
                setupLansiaDropdown(emptyList())
            }
        }
    }

    private fun setupLansiaDropdown(penghuniList: List<com.smartbelt.caregiver.data.model.Penghuni>) {
        if (penghuniList.isEmpty()) {
            binding.tilLansiaTarget.isEnabled = false
            binding.actvLansiaTarget.setText("Belum ada lansia terdaftar", false)
            selectedPenghuniId = null
        } else {
            binding.tilLansiaTarget.isEnabled = true
            val displayList = penghuniList.map { p ->
                val kamar = p.alamatKamar ?: "Wisma INAKAKA"
                "${p.namaLengkap} ($kamar)"
            }
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, displayList)
            binding.actvLansiaTarget.setAdapter(adapter)
            binding.actvLansiaTarget.setText(displayList[0], false)
            selectedPenghuniId = penghuniList[0].idPenghuni

            binding.actvLansiaTarget.setOnItemClickListener { _, _, position, _ ->
                if (position in penghuniList.indices) {
                    selectedPenghuniId = penghuniList[position].idPenghuni
                }
            }
        }
    }
}
