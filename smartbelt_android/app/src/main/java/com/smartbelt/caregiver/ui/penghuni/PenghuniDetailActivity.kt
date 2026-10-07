package com.smartbelt.caregiver.ui.penghuni

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smartbelt.caregiver.data.api.ApiClient
import com.smartbelt.caregiver.data.model.KontakDarurat
import com.smartbelt.caregiver.databinding.ActivityPenghuniDetailBinding
import com.smartbelt.caregiver.databinding.ItemKontakBinding
import kotlinx.coroutines.launch

class PenghuniDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPenghuniDetailBinding
    private val adapter = KontakAdapter { noHp ->
        val callIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$noHp")
        }
        startActivity(callIntent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPenghuniDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        val idPenghuni = intent.getIntExtra("ID_PENGHUNI", 1)
        val nama = intent.getStringExtra("NAMA_PENGHUNI") ?: "Bapak Yohannes Sitompul"
        val kamar = intent.getStringExtra("KAMAR") ?: "Kamar A-01"
        val riwayat = intent.getStringExtra("RIWAYAT") ?: "Riwayat stroke ringan, hipertensi"

        binding.tvDetailNama.text = nama
        binding.tvDetailKamar.text = "$kamar • Wisma Panti INAKAKA"
        binding.tvDetailRiwayat.text = riwayat

        binding.rvKontak.layoutManager = LinearLayoutManager(this)
        binding.rvKontak.adapter = adapter

        fetchKontakDarurat(idPenghuni)
    }

    private fun fetchKontakDarurat(idPenghuni: Int) {
        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(this@PenghuniDetailActivity)
                val res = api.getKontakByPenghuni(idPenghuni)

                if (res.isSuccessful && res.body()?.success == true) {
                    val list = res.body()?.data ?: emptyList()
                    adapter.submitList(list)
                }
            } catch (e: Exception) {
                Toast.makeText(this@PenghuniDetailActivity, "Gagal memuat kontak darurat", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

class KontakAdapter(private val onCallClick: (String) -> Unit) : RecyclerView.Adapter<KontakAdapter.ViewHolder>() {

    private val list = mutableListOf<KontakDarurat>()

    fun submitList(newList: List<KontakDarurat>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemKontakBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(list[position])
    }

    override fun getItemCount(): Int = list.size

    inner class ViewHolder(private val binding: ItemKontakBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(kontak: KontakDarurat) {
            binding.tvPriorityBadge.text = "${kontak.prioritas ?: 1}"
            binding.tvNamaKontak.text = kontak.namaKontak ?: "-"
            binding.tvNoHpKontak.text = "${kontak.noTelepon ?: "-"} • ${kontak.hubungan ?: "KELUARGA"}"

            binding.btnCallAction.setOnClickListener {
                kontak.noTelepon?.let { onCallClick(it) }
            }
        }
    }
}
