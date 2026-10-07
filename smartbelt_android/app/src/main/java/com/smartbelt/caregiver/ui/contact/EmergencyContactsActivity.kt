package com.smartbelt.caregiver.ui.contact

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smartbelt.caregiver.data.api.ApiClient
import com.smartbelt.caregiver.data.model.KontakDarurat
import com.smartbelt.caregiver.data.pref.SessionManager
import com.smartbelt.caregiver.databinding.ActivityEmergencyContactsBinding
import com.smartbelt.caregiver.databinding.DialogFormKontakBinding
import com.smartbelt.caregiver.databinding.ItemKontakDaruratBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EmergencyContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEmergencyContactsBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: EmergencyContactAdapter

    private var activePenghuniId: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmergencyContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        binding.btnBack.setOnClickListener { finish() }

        adapter = EmergencyContactAdapter(
            onCallClick = { phone ->
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phone")
                }
                startActivity(dialIntent)
            },
            onEditClick = { kontak ->
                showEditContactDialog(kontak)
            },
            onDeleteClick = { kontak ->
                confirmDeleteContact(kontak)
            }
        )

        binding.rvKontak.layoutManager = LinearLayoutManager(this)
        binding.rvKontak.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            loadContacts()
        }

        binding.btnAddContact.setOnClickListener {
            showAddContactDialog()
        }

        binding.btnAddTop.setOnClickListener {
            showAddContactDialog()
        }

        resolvePenghuniIdAndLoad()
    }

    private fun resolvePenghuniIdAndLoad() {
        val extraId = intent.getIntExtra("ID_PENGHUNI", 0)
        if (extraId > 0) {
            activePenghuniId = extraId
            loadContacts()
            return
        }

        val userId = sessionManager.getUserId()
        val role = sessionManager.getRole()?.uppercase() ?: "KELUARGA"

        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(this@EmergencyContactsActivity)
                if (role == "PENGGUNA_BELT" || role == "LANSIA") {
                    val res = api.getPenghuniByLansia(userId)
                    if (res.isSuccessful && res.body()?.data != null) {
                        activePenghuniId = res.body()?.data?.idPenghuni ?: 1
                    }
                } else {
                    val res = api.getPenghuniByKeluarga(userId)
                    if (res.isSuccessful && !res.body()?.data.isNullOrEmpty()) {
                        activePenghuniId = res.body()?.data?.first()?.idPenghuni ?: 1
                    }
                }
            } catch (e: Exception) {
                activePenghuniId = 1
            } finally {
                loadContacts()
            }
        }
    }

    private fun loadContacts() {
        binding.swipeRefresh.isRefreshing = true

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.getApiService(this@EmergencyContactsActivity).getKontakByPenghuni(activePenghuniId)
                }
                if (response.isSuccessful && response.body()?.success == true) {
                    val list = response.body()?.data ?: emptyList()
                    val sortedList = list.sortedBy { it.prioritas ?: 99 }
                    adapter.submitList(sortedList)

                    if (sortedList.isEmpty()) {
                        binding.layoutEmpty.visibility = View.VISIBLE
                        binding.rvKontak.visibility = View.GONE
                    } else {
                        binding.layoutEmpty.visibility = View.GONE
                        binding.rvKontak.visibility = View.VISIBLE
                    }
                } else {
                    Toast.makeText(this@EmergencyContactsActivity, "Gagal memuat kontak darurat", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@EmergencyContactsActivity, "Error koneksi: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun showAddContactDialog() {
        val dialogBinding = DialogFormKontakBinding.inflate(layoutInflater)
        dialogBinding.tvDialogTitle.text = "Tambah Kontak Darurat Baru"

        // Default next priority
        val currentMaxPriority = adapter.getCurrentList().maxOfOrNull { it.prioritas ?: 1 } ?: 0
        dialogBinding.etDialogPrioritas.setText((currentMaxPriority + 1).toString())

        AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Simpan") { _, _ ->
                val nama = dialogBinding.etDialogNama.text.toString().trim()
                val telepon = dialogBinding.etDialogTelepon.text.toString().trim()
                val hubungan = dialogBinding.etDialogHubungan.text.toString().trim()
                val prioritas = dialogBinding.etDialogPrioritas.text.toString().toIntOrNull() ?: 1

                if (nama.isEmpty() || telepon.isEmpty()) {
                    Toast.makeText(this, "Nama dan nomor telepon wajib diisi!", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newContact = KontakDarurat(
                    idKontak = null,
                    namaKontak = nama,
                    noTelepon = telepon,
                    hubungan = if (hubungan.isEmpty()) "KELUARGA" else hubungan.uppercase(),
                    prioritas = prioritas,
                    statusAktif = true
                )

                lifecycleScope.launch {
                    try {
                        val res = withContext(Dispatchers.IO) {
                            ApiClient.getApiService(this@EmergencyContactsActivity).addKontak(activePenghuniId, newContact)
                        }
                        if (res.isSuccessful && res.body()?.success == true) {
                            Toast.makeText(this@EmergencyContactsActivity, "✅ Kontak darurat berhasil ditambahkan!", Toast.LENGTH_SHORT).show()
                            loadContacts()
                        } else {
                            Toast.makeText(this@EmergencyContactsActivity, "Gagal menambahkan: ${res.message()}", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@EmergencyContactsActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showEditContactDialog(kontak: KontakDarurat) {
        val dialogBinding = DialogFormKontakBinding.inflate(layoutInflater)
        dialogBinding.tvDialogTitle.text = "Edit Kontak Darurat"
        dialogBinding.etDialogNama.setText(kontak.namaKontak ?: "")
        dialogBinding.etDialogTelepon.setText(kontak.noTelepon ?: "")
        dialogBinding.etDialogHubungan.setText(kontak.hubungan ?: "")
        dialogBinding.etDialogPrioritas.setText((kontak.prioritas ?: 1).toString())

        AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Simpan Perubahan") { _, _ ->
                val nama = dialogBinding.etDialogNama.text.toString().trim()
                val telepon = dialogBinding.etDialogTelepon.text.toString().trim()
                val hubungan = dialogBinding.etDialogHubungan.text.toString().trim()
                val prioritas = dialogBinding.etDialogPrioritas.text.toString().toIntOrNull() ?: 1

                if (nama.isEmpty() || telepon.isEmpty()) {
                    Toast.makeText(this, "Nama dan nomor telepon wajib diisi!", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val updatedContact = KontakDarurat(
                    idKontak = kontak.idKontak,
                    namaKontak = nama,
                    noTelepon = telepon,
                    hubungan = if (hubungan.isEmpty()) "KELUARGA" else hubungan.uppercase(),
                    prioritas = prioritas,
                    statusAktif = true
                )

                val idKontak = kontak.idKontak ?: return@setPositiveButton

                lifecycleScope.launch {
                    try {
                        val res = withContext(Dispatchers.IO) {
                            ApiClient.getApiService(this@EmergencyContactsActivity).updateKontak(idKontak, updatedContact)
                        }
                        if (res.isSuccessful && res.body()?.success == true) {
                            Toast.makeText(this@EmergencyContactsActivity, "✅ Kontak darurat berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                            loadContacts()
                        } else {
                            Toast.makeText(this@EmergencyContactsActivity, "Gagal memperbarui: ${res.message()}", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@EmergencyContactsActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmDeleteContact(kontak: KontakDarurat) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Kontak Darurat")
            .setMessage("Apakah Anda yakin ingin menghapus nomor ${kontak.namaKontak} (${kontak.noTelepon}) dari daftar prioritas panggilan darurat?")
            .setPositiveButton("Hapus") { _, _ ->
                val idKontak = kontak.idKontak ?: return@setPositiveButton
                lifecycleScope.launch {
                    try {
                        val res = withContext(Dispatchers.IO) {
                            ApiClient.getApiService(this@EmergencyContactsActivity).deleteKontak(idKontak)
                        }
                        if (res.isSuccessful && res.body()?.success == true) {
                            Toast.makeText(this@EmergencyContactsActivity, "Kontak berhasil dihapus", Toast.LENGTH_SHORT).show()
                            loadContacts()
                        } else {
                            Toast.makeText(this@EmergencyContactsActivity, "Gagal menghapus kontak", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@EmergencyContactsActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}

class EmergencyContactAdapter(
    private val onCallClick: (String) -> Unit,
    private val onEditClick: (KontakDarurat) -> Unit,
    private val onDeleteClick: (KontakDarurat) -> Unit
) : RecyclerView.Adapter<EmergencyContactAdapter.ViewHolder>() {

    private val items = mutableListOf<KontakDarurat>()

    fun submitList(newItems: List<KontakDarurat>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun getCurrentList(): List<KontakDarurat> = items

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemKontakDaruratBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val b: ItemKontakDaruratBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(k: KontakDarurat) {
            b.tvNamaKontak.text = k.namaKontak ?: "Tanpa Nama"
            b.tvNoTelepon.text = k.noTelepon ?: "-"
            b.tvHubunganBadge.text = k.hubungan ?: "LAINNYA"

            val prioritas = k.prioritas ?: 1
            if (prioritas == 1) {
                b.tvPrioritasBadge.text = "PRIORITAS 1 (UTAMA)"
                b.tvPrioritasBadge.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                b.tvPrioritasBadge.setBackgroundResource(com.smartbelt.caregiver.R.drawable.bg_status_danger)
            } else {
                b.tvPrioritasBadge.text = "PRIORITAS $prioritas"
                b.tvPrioritasBadge.setTextColor(android.graphics.Color.parseColor("#2563EB"))
                b.tvPrioritasBadge.setBackgroundResource(com.smartbelt.caregiver.R.drawable.bg_ios_dynamic_island)
            }

            b.btnCall.setOnClickListener {
                k.noTelepon?.let { phone -> onCallClick(phone) }
            }

            b.btnEdit.setOnClickListener {
                onEditClick(k)
            }

            b.btnDelete.setOnClickListener {
                onDeleteClick(k)
            }
        }
    }
}
