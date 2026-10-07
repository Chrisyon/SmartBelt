package com.smartbelt.caregiver.ui.admin

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smartbelt.caregiver.data.api.ApiClient
import com.smartbelt.caregiver.data.model.CreateUserRequest
import com.smartbelt.caregiver.data.model.User
import com.smartbelt.caregiver.databinding.ActivityUserEditBinding
import kotlinx.coroutines.launch

class UserEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserEditBinding
    private var mode = "EDIT"
    private var userId: Int = 0

    private val roles = arrayOf("CAREGIVER", "KELUARGA", "PENGGUNA_BELT", "ADMIN")

    private var listPenghuni: List<com.smartbelt.caregiver.data.model.Penghuni> = emptyList()
    private var selectedPenghuniId: Int? = null
    private val hubunganList = listOf("Anak", "Cucu", "Pasangan", "Menantu", "Saudara", "Kerabat")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mode = intent.getStringExtra("MODE") ?: "EDIT"
        userId = intent.getIntExtra("USER_ID", 0)

        binding.btnBack.setOnClickListener { finish() }

        setupRoleDropdown()
        populateData()

        binding.btnSaveUser.setOnClickListener {
            performSave()
        }
    }

    private fun setupRoleDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, roles)
        binding.actvRole.setAdapter(adapter)
        binding.actvRole.setOnItemClickListener { _, _, _, _ ->
            updateRoleSection()
        }
    }

    private fun updateRoleSection() {
        val role = binding.actvRole.text.toString().trim()
        if (role.equals("KELUARGA", ignoreCase = true)) {
            binding.cardKeluargaRelasi.visibility = View.VISIBLE
            loadPenghuniAndKeluargaRelation()
        } else {
            binding.cardKeluargaRelasi.visibility = View.GONE
        }
    }

    private fun loadPenghuniAndKeluargaRelation() {
        val hubAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, hubunganList)
        binding.actvHubunganKeluarga.setAdapter(hubAdapter)

        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(this@UserEditActivity)
                val resPenghuni = api.getAllPenghuni()
                if (resPenghuni.isSuccessful && resPenghuni.body()?.success == true) {
                    listPenghuni = resPenghuni.body()?.data ?: emptyList()
                    setupLansiaDropdown(listPenghuni)
                }

                if (mode == "EDIT" && userId > 0) {
                    val resRelasi = api.getUserKeluarga(userId)
                    if (resRelasi.isSuccessful && resRelasi.body()?.success == true) {
                        val relasi = resRelasi.body()?.data
                        if (relasi != null && relasi.idPenghuni != null) {
                            selectedPenghuniId = relasi.idPenghuni
                            val found = listPenghuni.find { it.idPenghuni == relasi.idPenghuni }
                            val display = if (found != null) {
                                "${found.namaLengkap} (${found.alamatKamar ?: "Wisma"})"
                            } else {
                                "${relasi.namaLengkap} (${relasi.alamatKamar ?: "Wisma"})"
                            }
                            binding.actvLansiaTarget.setText(display, false)
                            relasi.hubungan?.let {
                                binding.actvHubunganKeluarga.setText(it, false)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // error loading
            }
        }
    }

    private fun setupLansiaDropdown(penghuniList: List<com.smartbelt.caregiver.data.model.Penghuni>) {
        if (penghuniList.isEmpty()) {
            binding.tilLansiaTarget.isEnabled = false
            binding.actvLansiaTarget.setText("Belum ada lansia", false)
            selectedPenghuniId = null
        } else {
            binding.tilLansiaTarget.isEnabled = true
            val displayList = penghuniList.map { p ->
                val kamar = p.alamatKamar ?: "Wisma INAKAKA"
                "${p.namaLengkap} ($kamar)"
            }
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, displayList)
            binding.actvLansiaTarget.setAdapter(adapter)

            binding.actvLansiaTarget.setOnItemClickListener { _, _, position, _ ->
                if (position in penghuniList.indices) {
                    selectedPenghuniId = penghuniList[position].idPenghuni
                }
            }
        }
    }

    private fun populateData() {
        if (mode == "CREATE") {
            binding.tvTitle.text = "Tambah Pengguna Baru"
            binding.btnSaveUser.text = "TAMBAH PENGGUNA"
            binding.tilPassword.hint = "Password (Wajib diisi)"
            binding.actvRole.setText("CAREGIVER", false)
            updateRoleSection()
        } else {
            binding.tvTitle.text = "Edit Data Pengguna"
            binding.btnSaveUser.text = "SIMPAN PERUBAHAN"
            binding.etNamaLengkap.setText(intent.getStringExtra("NAMA_LENGKAP"))
            binding.etUsername.setText(intent.getStringExtra("USERNAME"))
            binding.etUsername.isEnabled = false // Username tidak bisa diganti pada mode edit
            binding.etEmail.setText(intent.getStringExtra("EMAIL"))
            binding.etPhone.setText(intent.getStringExtra("NO_TELEPON"))
            binding.etTelegramChatId.setText(intent.getStringExtra("TELEGRAM_CHAT_ID"))
            binding.switchStatusAktif.isChecked = intent.getBooleanExtra("STATUS_AKTIF", true)

            val role = intent.getStringExtra("ROLE") ?: "CAREGIVER"
            binding.actvRole.setText(role, false)
            updateRoleSection()
        }
    }

    private fun performSave() {
        val nama = binding.etNamaLengkap.text.toString().trim()
        val username = binding.etUsername.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val telegramChatId = binding.etTelegramChatId.text.toString().trim()
        val role = binding.actvRole.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val statusAktif = binding.switchStatusAktif.isChecked

        if (nama.isEmpty()) {
            binding.tilNamaLengkap.error = "Nama lengkap wajib diisi"
            return
        }
        binding.tilNamaLengkap.error = null

        if (email.isEmpty() || !email.contains("@")) {
            binding.tilEmail.error = "Email valid wajib diisi"
            return
        }
        binding.tilEmail.error = null

        if (mode == "CREATE") {
            if (username.isEmpty()) {
                binding.tilUsername.error = "Username wajib diisi"
                return
            }
            binding.tilUsername.error = null

            if (password.length < 6) {
                binding.tilPassword.error = "Password minimal 6 karakter"
                return
            }
            binding.tilPassword.error = null
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSaveUser.isEnabled = false

        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(this@UserEditActivity)

                val userPayload = User(
                    idUser = if (mode == "EDIT") userId else null,
                    namaLengkap = nama,
                    username = if (mode == "CREATE") username else null,
                    email = email,
                    noTelepon = phone,
                    role = role,
                    statusAktif = statusAktif,
                    passwordHash = if (password.isNotEmpty()) password else null,
                    telegramChatId = telegramChatId.ifEmpty { null }
                )

                val response = if (mode == "CREATE") {
                    val createReq = CreateUserRequest(
                        namaLengkap = nama,
                        username = username,
                        email = email,
                        noTelepon = phone,
                        role = role,
                        password = if (password.isNotEmpty()) password else "123456",
                        telegramChatId = telegramChatId.ifEmpty { null }
                    )
                    api.createUser(createReq)
                } else {
                    api.updateUser(userId, userPayload)
                }

                binding.progressBar.visibility = View.GONE
                binding.btnSaveUser.isEnabled = true

                if (response.isSuccessful && response.body()?.success == true) {
                    if (role.equals("KELUARGA", ignoreCase = true) && selectedPenghuniId != null) {
                        val hub = binding.actvHubunganKeluarga.text.toString().trim().ifEmpty { "Keluarga" }
                        try {
                            api.updateUserKeluarga(userId, mapOf("idPenghuniTarget" to selectedPenghuniId, "hubunganKeluarga" to hub))
                        } catch (ignored: Exception) {}
                    }
                    Toast.makeText(this@UserEditActivity, response.body()?.message ?: "Berhasil disimpan!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    val errMsg = response.body()?.message ?: "Gagal menyimpan data"
                    Toast.makeText(this@UserEditActivity, "❌ $errMsg", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnSaveUser.isEnabled = true
                Toast.makeText(this@UserEditActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
