package com.smartbelt.caregiver.ui.admin

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smartbelt.caregiver.data.api.ApiClient
import com.smartbelt.caregiver.data.model.CreateUserRequest
import com.smartbelt.caregiver.data.model.Penghuni
import com.smartbelt.caregiver.databinding.ActivityUserCreateBinding
import kotlinx.coroutines.launch
import java.util.Calendar

class UserCreateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserCreateBinding
    private var listPenghuni: List<Penghuni> = emptyList()
    private var selectedPenghuniId: Int? = null
    private var selectedRole: String = "KELUARGA"

    private val rolesDisplay = listOf(
        "Keluarga Lansia (KELUARGA)",
        "Lansia Pengguna Sabuk (PENGGUNA_BELT)",
        "Perawat / Caregiver (CAREGIVER)",
        "Administrator (ADMIN)"
    )

    private val roleValues = listOf(
        "KELUARGA",
        "PENGGUNA_BELT",
        "CAREGIVER",
        "ADMIN"
    )

    private val hubunganList = listOf("Anak", "Cucu", "Pasangan", "Saudara", "Wali", "Lainnya")
    private val genderList = listOf("Laki-laki", "Perempuan")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserCreateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        setupRoleDropdown()
        setupHubunganDropdown()
        setupGenderDropdown()
        setupDatePicker()
        fetchPenghuniList()

        binding.btnSaveUser.setOnClickListener {
            submitCreateUser()
        }
    }

    private fun setupRoleDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, rolesDisplay)
        binding.actvRole.setAdapter(adapter)
        binding.actvRole.setText(rolesDisplay[0], false)

        binding.actvRole.setOnItemClickListener { _, _, position, _ ->
            selectedRole = roleValues[position]
            updateRoleVisibility(selectedRole)
        }

        updateRoleVisibility(selectedRole)
    }

    private fun setupHubunganDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, hubunganList)
        binding.actvHubunganKeluarga.setAdapter(adapter)
        binding.actvHubunganKeluarga.setText(hubunganList[0], false)
    }

    private fun setupGenderDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genderList)
        binding.actvJenisKelamin.setAdapter(adapter)
        binding.actvJenisKelamin.setText(genderList[0], false)
    }

    private fun setupDatePicker() {
        binding.etTanggalLahir.setOnClickListener {
            val cal = Calendar.getInstance()
            cal.add(Calendar.YEAR, -70)
            val dpd = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val dateStr = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                    binding.etTanggalLahir.setText(dateStr)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            )
            dpd.show()
        }
    }

    private fun updateRoleVisibility(role: String) {
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
                val api = ApiClient.getApiService(this@UserCreateActivity)
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

    private fun setupLansiaDropdown(penghuniList: List<Penghuni>) {
        if (penghuniList.isEmpty()) {
            binding.layoutNoLansiaWarning.visibility = View.VISIBLE
            binding.tilSelectLansia.isEnabled = false
            binding.actvLansia.setText("Belum ada lansia terdaftar", false)
            selectedPenghuniId = null
        } else {
            binding.layoutNoLansiaWarning.visibility = View.GONE
            binding.tilSelectLansia.isEnabled = true

            val lansiaDisplayList = penghuniList.map { p ->
                val kamar = p.alamatKamar ?: "Wisma INAKAKA"
                "${p.namaLengkap} ($kamar)"
            }

            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, lansiaDisplayList)
            binding.actvLansia.setAdapter(adapter)
            binding.actvLansia.setText(lansiaDisplayList[0], false)
            selectedPenghuniId = penghuniList[0].idPenghuni

            binding.actvLansia.setOnItemClickListener { _, _, position, _ ->
                if (position in penghuniList.indices) {
                    selectedPenghuniId = penghuniList[position].idPenghuni
                }
            }
        }
    }

    private fun submitCreateUser() {
        val nama = binding.etNamaLengkap.text?.toString()?.trim().orEmpty()
        val username = binding.etUsername.text?.toString()?.trim().orEmpty()
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val telepon = binding.etNoTelepon.text?.toString()?.trim().orEmpty()
        val telegramChatId = binding.etTelegramChatId.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString()?.trim().orEmpty()
        val alamatKamar = binding.etAlamatKamar.text?.toString()?.trim().orEmpty()
        val tanggalLahir = binding.etTanggalLahir.text?.toString()?.trim().orEmpty()
        val jenisKelaminText = binding.actvJenisKelamin.text?.toString()?.trim().orEmpty()
        val riwayatPenyakit = binding.etRiwayatPenyakit.text?.toString()?.trim().orEmpty()
        val hubunganKeluarga = binding.actvHubunganKeluarga.text?.toString()?.trim().orEmpty()

        if (nama.isEmpty()) {
            binding.etNamaLengkap.error = "Nama lengkap wajib diisi"
            binding.etNamaLengkap.requestFocus()
            return
        }

        if (username.isEmpty()) {
            binding.etUsername.error = "Username wajib diisi"
            binding.etUsername.requestFocus()
            return
        }

        if (email.isEmpty()) {
            binding.etEmail.error = "Email wajib diisi"
            binding.etEmail.requestFocus()
            return
        }

        if (selectedRole == "KELUARGA") {
            if (listPenghuni.isEmpty()) {
                Toast.makeText(
                    this,
                    "Belum ada lansia terdaftar! Daftarkan lansia terlebih dahulu sebelum mendaftarkan keluarga.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            if (selectedPenghuniId == null) {
                Toast.makeText(this, "Pilih lansia yang didampingi oleh akun keluarga ini!", Toast.LENGTH_SHORT).show()
                return
            }
        }

        setLoading(true)

        val req = CreateUserRequest(
            namaLengkap = nama,
            username = username,
            email = email,
            noTelepon = telepon.ifEmpty { null },
            telegramChatId = telegramChatId.ifEmpty { null },
            password = password.ifEmpty { "123456" },
            role = selectedRole,
            idPenghuni = if (selectedRole == "KELUARGA") selectedPenghuniId else null,
            idPenghuniTarget = if (selectedRole == "KELUARGA") selectedPenghuniId else null,
            hubunganKeluarga = if (selectedRole == "KELUARGA") hubunganKeluarga.ifEmpty { "Keluarga" } else null,
            alamatKamar = if (selectedRole == "PENGGUNA_BELT") alamatKamar.ifEmpty { null } else null,
            tanggalLahir = if (selectedRole == "PENGGUNA_BELT") tanggalLahir.ifEmpty { null } else null,
            jenisKelamin = if (selectedRole == "PENGGUNA_BELT") (if (jenisKelaminText.contains("Perempuan")) "P" else "L") else null,
            riwayatPenyakit = if (selectedRole == "PENGGUNA_BELT") riwayatPenyakit.ifEmpty { null } else null
        )

        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(this@UserCreateActivity)
                val res = api.createUser(req)
                setLoading(false)

                if (res.isSuccessful && res.body()?.success == true) {
                    Toast.makeText(
                        this@UserCreateActivity,
                        "Pengguna '${nama}' (${selectedRole}) berhasil didaftarkan!",
                        Toast.LENGTH_LONG
                    ).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    val errMsg = res.body()?.message ?: "Gagal menambahkan pengguna"
                    Toast.makeText(this@UserCreateActivity, errMsg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(this@UserCreateActivity, "Koneksi gagal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSaveUser.isEnabled = !isLoading
    }
}
