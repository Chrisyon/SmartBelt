package com.smartbelt.caregiver.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smartbelt.caregiver.R
import com.smartbelt.caregiver.data.api.ApiClient
import com.smartbelt.caregiver.data.model.KejadianDarurat
import com.smartbelt.caregiver.databinding.ActivityIncidentHistoryBinding
import com.smartbelt.caregiver.databinding.ItemIncidentBinding
import kotlinx.coroutines.launch
import java.util.Locale

class IncidentHistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityIncidentHistoryBinding
    private val adapter = IncidentAdapter { item ->
        showDetailDialog(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIncidentHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.rvIncidents.layoutManager = LinearLayoutManager(this)
        binding.rvIncidents.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { fetchHistory() }

        fetchHistory()

    }

    private fun showDetailDialog(k: KejadianDarurat) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.dialog_riwayat_kejadian, null)
        view.setBackgroundResource(R.drawable.bg_rounded_dialog)
        builder.setView(view)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        // PANGGIL SVM VALUE DAN NAMA DARI ACTIVITY:
        val namaLansia = k.penghuni?.namaLengkap ?: "Lansia"
        val nilaiSvm = k.svmValue ?: 0.0
        view.findViewById<TextView>(R.id.tvDetailNama)?.text = "Nama: $namaLansia"
        view.findViewById<TextView>(R.id.tvDetailSvm)?.text = "Benturan (SVM): ${nilaiSvm}g"
        view.findViewById<Button>(R.id.btnTutup)?.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun fetchHistory() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(this@IncidentHistoryActivity)
                val res = api.getAllKejadian()

                binding.swipeRefresh.isRefreshing = false
                if (res.isSuccessful && res.body()?.success == true) {
                    val list = res.body()?.data ?: emptyList()
                    adapter.submitList(list)
                } else {
                    Toast.makeText(this@IncidentHistoryActivity, "Gagal memuat riwayat insiden", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(this@IncidentHistoryActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

class IncidentAdapter(
    private val onItemClick: (KejadianDarurat) -> Unit
) : RecyclerView.Adapter<IncidentAdapter.ViewHolder>() {
    private val list = mutableListOf<KejadianDarurat>()

    fun submitList(newList: List<KejadianDarurat>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemIncidentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(list[position])
    }

    override fun getItemCount(): Int = list.size

    inner class ViewHolder(private val binding: ItemIncidentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(k: KejadianDarurat) {
            val context = binding.root.context
            binding.tvIncidentType.text = k.jenisKejadian ?: "JATUH"
            binding.tvIncidentResident.text = "${k.penghuni?.namaLengkap ?: "Lansia"} (${k.penghuni?.alamatKamar ?: "-"})"
            binding.tvIncidentTime.text = "Waktu: ${k.waktuKejadian ?: "-"}"
            binding.tvLongLat.text = "Longtitude Latitude: ${k.longitude ?: "-"} ${k.latitude ?: "-"}"
            binding.tvIncidentSvm.text = String.format(
                Locale.US,
                "Dampak SVM: %.3fg • Sudut Pitch: %+.1f°",
                k.svmValue ?: 0.0,
                k.pitchValue ?: 0.0
            )

            binding.tvIncidentStatusBadge.text = k.statusKejadian ?: "TERCATAT"

            when (k.statusKejadian) {
                "AUTO_CALL" -> {
                    binding.ivIncidentIcon.setImageResource(R.drawable.ic_warning_triangle)
                    binding.ivIncidentIcon.setColorFilter(ContextCompat.getColor(context, R.color.accent_emergency))
                    binding.tvIncidentStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.accent_emergency))
                }
                "DIBATALKAN" -> {
                    binding.ivIncidentIcon.setImageResource(R.drawable.ic_sos)
                    binding.ivIncidentIcon.setColorFilter(ContextCompat.getColor(context, R.color.accent_warning))
                    binding.tvIncidentStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.accent_warning))
                }
                "SELESAI" -> {
                    binding.ivIncidentIcon.setImageResource(R.drawable.ic_check_circle)
                    binding.ivIncidentIcon.setColorFilter(ContextCompat.getColor(context, R.color.accent_success))
                    binding.tvIncidentStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.accent_success))
                }
                else -> {
                    binding.ivIncidentIcon.setImageResource(R.drawable.ic_warning_triangle)
                    binding.ivIncidentIcon.setColorFilter(ContextCompat.getColor(context, R.color.accent_emergency))
                }
            }
            binding.root.setOnClickListener {
                val builder = androidx.appcompat.app.AlertDialog.Builder(context)
                val view = LayoutInflater.from(context).inflate(R.layout.dialog_riwayat_kejadian, null)
                // Pasang background rounded yang punya radius
                view.setBackgroundResource(R.drawable.bg_rounded_dialog)
                builder.setView(view)
                val dialog = builder.create()
                // Wajib: Bikin background bawaan transparan agar sudut melengkungnya kelihatan!
                dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                // Isi Data
                view.findViewById<TextView>(R.id.tvDetailNama).text = "Nama: ${k.penghuni?.namaLengkap ?: "Lansia"}"
                view.findViewById<TextView>(R.id.tvDetailSvm).text = "Benturan: ${k.svmValue ?: 0.0}g"
                // Tombol Tutup
                view.findViewById<Button>(R.id.btnTutup).setOnClickListener {
                    dialog.dismiss()
                }
                dialog.show()
            }
        }
    }
}
