package com.smartbelt.caregiver.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.smartbelt.caregiver.R
import com.smartbelt.caregiver.data.model.KejadianDarurat
import com.smartbelt.caregiver.databinding.ItemIncidentBinding

class RiwayatAdapter : RecyclerView.Adapter<RiwayatAdapter.ViewHolder>() {

    private val list = mutableListOf<KejadianDarurat>()

    // 1. Fungsi untuk memasukkan data dari Activity ke Adapter
    fun submitList(newList: List<KejadianDarurat>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }

    // 2. Hubungkan desain item XML (ItemIncidentBinding)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemIncidentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    // 3. Panggil bind untuk setiap baris
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(list[position])
    }

    // 4. Hitung jumlah item
    override fun getItemCount(): Int = list.size

    // 5. Kelas ViewHolder (tempat ngisi data & pasang KLIK CARD)
    inner class ViewHolder(private val binding: ItemIncidentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: KejadianDarurat) {
            val context = binding.root.context
            binding.tvIncidentType.text = item.jenisKejadian

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
                view.findViewById<TextView>(R.id.tvDetailNama).text = "Nama: ${item.penghuni?.namaLengkap ?: "Lansia"}"
                view.findViewById<TextView>(R.id.tvDetailSvm).text = "Benturan: ${item.svmValue ?: 0.0}g"
                // Tombol Tutup
                view.findViewById<Button>(R.id.btnTutup).setOnClickListener {
                    dialog.dismiss()
                }
                dialog.show()
            }
        }
    }
}