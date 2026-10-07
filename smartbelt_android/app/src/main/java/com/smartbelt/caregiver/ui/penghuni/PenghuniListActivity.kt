package com.smartbelt.caregiver.ui.penghuni

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smartbelt.caregiver.data.api.ApiClient
import com.smartbelt.caregiver.data.model.Penghuni
import com.smartbelt.caregiver.databinding.ActivityPenghuniListBinding
import com.smartbelt.caregiver.databinding.ItemPenghuniBinding
import kotlinx.coroutines.launch

class PenghuniListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPenghuniListBinding
    private lateinit var adapter: PenghuniAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPenghuniListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        adapter = PenghuniAdapter(
            onItemClick = { penghuni ->
                val intent = Intent(this, PenghuniDetailActivity::class.java).apply {
                    putExtra("ID_PENGHUNI", penghuni.idPenghuni ?: 1)
                    putExtra("NAMA_PENGHUNI", penghuni.namaLengkap ?: "Lansia")
                    putExtra("KAMAR", penghuni.alamatKamar ?: "-")
                    putExtra("RIWAYAT", penghuni.riwayatPenyakit ?: "Tidak ada catatan")
                }
                startActivity(intent)
            },
            onEditClick = { penghuni ->
                openEditPenghuni(penghuni)
            }
        )

        binding.rvPenghuni.layoutManager = LinearLayoutManager(this)
        binding.rvPenghuni.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { fetchPenghuni() }

        binding.fabAddPenghuni.setOnClickListener {
            val intent = Intent(this, PenghuniEditActivity::class.java).apply {
                putExtra("MODE", "CREATE")
            }
            startActivity(intent)
        }

        fetchPenghuni()
    }

    override fun onResume() {
        super.onResume()
        fetchPenghuni()
    }

    private fun openEditPenghuni(penghuni: Penghuni) {
        val intent = Intent(this, PenghuniEditActivity::class.java).apply {
            putExtra("MODE", "EDIT")
            putExtra("ID_PENGHUNI", penghuni.idPenghuni ?: 0)
            putExtra("NAMA_LENGKAP", penghuni.namaLengkap)
            putExtra("TANGGAL_LAHIR", penghuni.tanggalLahir)
            putExtra("JENIS_KELAMIN", penghuni.jenisKelamin)
            putExtra("ALAMAT_KAMAR", penghuni.alamatKamar)
            putExtra("NO_TELEPON", penghuni.noTelepon)
            putExtra("RIWAYAT_PENYAKIT", penghuni.riwayatPenyakit)
            putExtra("STATUS_AKTIF", penghuni.statusAktif ?: true)
            putExtra("ID_PERANGKAT", penghuni.idPerangkat ?: 0)
            putExtra("ID_USER_LANSIA", penghuni.idUserLansia ?: 0)
        }
        startActivity(intent)
    }

    private fun fetchPenghuni() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(this@PenghuniListActivity)
                val res = api.getAllPenghuni()

                binding.swipeRefresh.isRefreshing = false
                if (res.isSuccessful && res.body()?.success == true) {
                    val list = res.body()?.data ?: emptyList()
                    adapter.submitList(list)
                } else {
                    Toast.makeText(this@PenghuniListActivity, "Gagal memuat data penghuni", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(this@PenghuniListActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

class PenghuniAdapter(
    private val onItemClick: (Penghuni) -> Unit,
    private val onEditClick: (Penghuni) -> Unit
) : RecyclerView.Adapter<PenghuniAdapter.ViewHolder>() {

    private val list = mutableListOf<Penghuni>()

    fun submitList(newList: List<Penghuni>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPenghuniBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(list[position])
    }

    override fun getItemCount(): Int = list.size

    inner class ViewHolder(private val binding: ItemPenghuniBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(penghuni: Penghuni) {
            binding.tvNamaPenghuni.text = penghuni.namaLengkap ?: "-"
            val jk = if (penghuni.jenisKelamin == "P") "Perempuan" else "Laki-laki"
            val kamar = penghuni.alamatKamar ?: "Kamar -"
            binding.tvKamar.text = "$kamar • $jk"
            binding.tvRiwayatPenyakit.text = "Riwayat Medis: ${penghuni.riwayatPenyakit ?: "Tidak ada catatan"}"

            // Status Aktif
            if (penghuni.statusAktif == true) {
                binding.tvStatusBadge.text = "AKTIF"
                binding.tvStatusBadge.setTextColor(android.graphics.Color.parseColor("#16A34A"))
            } else {
                binding.tvStatusBadge.text = "NONAKTIF"
                binding.tvStatusBadge.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
            }

            // Perangkat Sabuk
            if (!penghuni.namaPerangkat.isNullOrEmpty()) {
                binding.tvDeviceBadge.text = "📟 Sabuk: ${penghuni.namaPerangkat}"
                binding.tvDeviceBadge.setTextColor(android.graphics.Color.parseColor("#16A34A"))
            } else {
                binding.tvDeviceBadge.text = "⚠️ Belum ada sabuk terpasang"
                binding.tvDeviceBadge.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
            }

            binding.btnEditPenghuni.setOnClickListener {
                onEditClick(penghuni)
            }

            binding.root.setOnClickListener {
                onItemClick(penghuni)
            }
        }
    }
}
