package com.smartbelt.caregiver.ui.penghuni

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smartbelt.caregiver.data.api.ApiClient
import com.smartbelt.caregiver.data.model.Penghuni
import com.smartbelt.caregiver.data.model.Perangkat
import com.smartbelt.caregiver.data.model.User
import com.smartbelt.caregiver.databinding.ActivityPenghuniEditBinding
import kotlinx.coroutines.launch
import java.util.Calendar

class PenghuniEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPenghuniEditBinding
    private var mode = "CREATE"
    private var idPenghuni = 0

    private var listPerangkat: List<Perangkat> = emptyList()
    private var selectedPerangkatId: Int? = null

    private var listUserLansia: List<User> = emptyList()
    private var selectedUserLansiaId: Int? = null

    private val genderList = listOf("Laki-laki (L)", "Perempuan (P)")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPenghuniEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mode = intent.getStringExtra("MODE") ?: "CREATE"
        idPenghuni = intent.getIntExtra("ID_PENGHUNI", 0)

        binding.btnBack.setOnClickListener { finish() }

        setupGenderDropdown()
        setupDatePicker()
        loadPerangkatAndUsers()

        if (mode == "EDIT") {
            binding.tvTitle.text = "Edit Data Lansia"
            binding.btnSavePenghuni.text = "SIMPAN PERUBAHAN"
            binding.btnDeletePenghuni.visibility = View.VISIBLE
            populateInitialData()
        } else {
            binding.tvTitle.text = "Tambah Lansia Baru"
            binding.btnSavePenghuni.text = "TAMBAH LANSIA"
            binding.btnDeletePenghuni.visibility = View.GONE
        }

        binding.btnSavePenghuni.setOnClickListener {
            performSave()
        }

        binding.btnDeletePenghuni.setOnClickListener {
            confirmDelete()
        }
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

    private fun populateInitialData() {
        binding.etNamaLengkap.setText(intent.getStringExtra("NAMA_LENGKAP"))
        binding.etTanggalLahir.setText(intent.getStringExtra("TANGGAL_LAHIR"))
        val jk = intent.getStringExtra("JENIS_KELAMIN")
        if (jk == "P") {
            binding.actvJenisKelamin.setText(genderList[1], false)
        } else {
            binding.actvJenisKelamin.setText(genderList[0], false)
        }
        binding.etAlamatKamar.setText(intent.getStringExtra("ALAMAT_KAMAR"))
        binding.etNoTelepon.setText(intent.getStringExtra("NO_TELEPON"))
        binding.etRiwayatPenyakit.setText(intent.getStringExtra("RIWAYAT_PENYAKIT"))
        binding.switchStatusAktif.isChecked = intent.getBooleanExtra("STATUS_AKTIF", true)

        selectedPerangkatId = intent.getIntExtra("ID_PERANGKAT", 0).takeIf { it > 0 }
        selectedUserLansiaId = intent.getIntExtra("ID_USER_LANSIA", 0).takeIf { it > 0 }
    }

    private fun loadPerangkatAndUsers() {
        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(this@PenghuniEditActivity)

                // 1. Load Perangkat
                val resPerangkat = api.getAllPerangkat()
                if (resPerangkat.isSuccessful && resPerangkat.body()?.success == true) {
                    listPerangkat = resPerangkat.body()?.data ?: emptyList()
                    setupPerangkatDropdown()
                }

                // 2. Load User Lansia
                val resUsers = api.getAllUsers()
                if (resUsers.isSuccessful && resUsers.body()?.success == true) {
                    listUserLansia = resUsers.body()?.data?.filter {
                        it.role.equals("PENGGUNA_BELT", ignoreCase = true) || it.role.equals("LANSIA", ignoreCase = true)
                    } ?: emptyList()
                    setupUserLansiaDropdown()
                }
            } catch (e: Exception) {
                // Ignore load error
            }
        }
    }

    private fun setupPerangkatDropdown() {
        val displayList = mutableListOf("Tidak ada / Belum dipasangkan")
        displayList.addAll(listPerangkat.map { "${it.namaPerangkat ?: "Smart-Belt"} (${it.macAddress ?: "-"})" })

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, displayList)
        binding.actvPerangkat.setAdapter(adapter)

        // Pre-select
        if (selectedPerangkatId != null) {
            val idx = listPerangkat.indexOfFirst { it.idPerangkat == selectedPerangkatId }
            if (idx >= 0) {
                binding.actvPerangkat.setText(displayList[idx + 1], false)
            }
        } else {
            binding.actvPerangkat.setText(displayList[0], false)
        }

        binding.actvPerangkat.setOnItemClickListener { _, _, position, _ ->
            selectedPerangkatId = if (position == 0) null else listPerangkat.getOrNull(position - 1)?.idPerangkat
        }
    }

    private fun setupUserLansiaDropdown() {
        val displayList = mutableListOf("Belum ditautkan ke akun login")
        displayList.addAll(listUserLansia.map { "${it.namaLengkap ?: it.username} (@${it.username})" })

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, displayList)
        binding.actvUserLansia.setAdapter(adapter)

        // Pre-select
        if (selectedUserLansiaId != null) {
            val idx = listUserLansia.indexOfFirst { it.idUser == selectedUserLansiaId }
            if (idx >= 0) {
                binding.actvUserLansia.setText(displayList[idx + 1], false)
            }
        } else {
            binding.actvUserLansia.setText(displayList[0], false)
        }

        binding.actvUserLansia.setOnItemClickListener { _, _, position, _ ->
            selectedUserLansiaId = if (position == 0) null else listUserLansia.getOrNull(position - 1)?.idUser
        }
    }

    private fun performSave() {
        val nama = binding.etNamaLengkap.text.toString().trim()
        val kamar = binding.etAlamatKamar.text.toString().trim()
        val tglLahir = binding.etTanggalLahir.text.toString().trim().ifEmpty { null }
        val jk = if (binding.actvJenisKelamin.text.toString().contains("Perempuan", ignoreCase = true)) "P" else "L"
        val phone = binding.etNoTelepon.text.toString().trim().ifEmpty { null }
        val riwayat = binding.etRiwayatPenyakit.text.toString().trim().ifEmpty { null }
        val aktif = binding.switchStatusAktif.isChecked

        if (nama.isEmpty()) {
            binding.tilNamaLengkap.error = "Nama lengkap lansia wajib diisi"
            binding.etNamaLengkap.requestFocus()
            return
        }
        binding.tilNamaLengkap.error = null

        if (kamar.isEmpty()) {
            binding.tilAlamatKamar.error = "Nomor / nama kamar wajib diisi"
            binding.etAlamatKamar.requestFocus()
            return
        }
        binding.tilAlamatKamar.error = null

        val payload = Penghuni(
            idPenghuni = if (mode == "EDIT") idPenghuni else null,
            namaLengkap = nama,
            tanggalLahir = tglLahir,
            jenisKelamin = jk,
            alamatKamar = kamar,
            noTelepon = phone,
            riwayatPenyakit = riwayat,
            statusAktif = aktif,
            idPerangkat = selectedPerangkatId,
            idUserLansia = selectedUserLansiaId
        )

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSavePenghuni.isEnabled = false

        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(this@PenghuniEditActivity)
                val response = if (mode == "CREATE") {
                    api.createPenghuni(payload)
                } else {
                    api.updatePenghuni(idPenghuni, payload)
                }

                binding.progressBar.visibility = View.GONE
                binding.btnSavePenghuni.isEnabled = true

                if (response.isSuccessful && response.body()?.success == true) {
                    val msg = if (mode == "CREATE") "Lansia baru berhasil ditambahkan!" else "Data lansia berhasil diperbarui!"
                    Toast.makeText(this@PenghuniEditActivity, msg, Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    val errMsg = response.body()?.message ?: "Gagal menyimpan data"
                    Toast.makeText(this@PenghuniEditActivity, "❌ $errMsg", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnSavePenghuni.isEnabled = true
                Toast.makeText(this@PenghuniEditActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Hapus / Nonaktifkan Lansia")
            .setMessage("Apakah Anda yakin ingin menonaktifkan data lansia ini? Perangkat Smart-Belt yang terpasang akan dilepaskan secara otomatis.")
            .setPositiveButton("Ya, Nonaktifkan") { _, _ ->
                performDelete()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun performDelete() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(this@PenghuniEditActivity)
                val res = api.deletePenghuni(idPenghuni)
                binding.progressBar.visibility = View.GONE
                if (res.isSuccessful && res.body()?.success == true) {
                    Toast.makeText(this@PenghuniEditActivity, "Lansia berhasil dinonaktifkan", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@PenghuniEditActivity, res.body()?.message ?: "Gagal menonaktifkan", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@PenghuniEditActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
