package com.smartbelt.caregiver.ui.admin

//Mengatur cara menampilkan daftar akun pengguna/perawat ke dalam daftar baris (RecyclerView) di layar HP.

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.smartbelt.caregiver.R
import com.smartbelt.caregiver.data.model.User
import com.smartbelt.caregiver.databinding.ItemUserBinding

class UserAdapter(
    private val onItemClick: (User) -> Unit,
    private val onApproveClick: ((User) -> Unit)? = null
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    private val userList = mutableListOf<User>()

    fun submitList(newList: List<User>) {
        userList.clear()
        userList.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(userList[position])
    }

    override fun getItemCount(): Int = userList.size

    inner class UserViewHolder(private val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            binding.tvUserName.text = user.namaLengkap ?: "-"
            binding.tvUserEmail.text = user.email ?: "-"
            binding.tvUserPhone.text = user.noTelepon ?: "Tidak ada no telepon"

            // Role formatting & Badge colors
            val role = user.role ?: "CAREGIVER"
            binding.tvUserRoleBadge.text = role

            when (role) {
                "ADMIN" -> {
                    binding.tvUserRoleBadge.setTextColor(Color.parseColor("#10B981"))
                    binding.cardAvatar.strokeColor = Color.parseColor("#34D399")
                    binding.cardAvatar.setCardBackgroundColor(Color.parseColor("#ECFDF5"))
                    binding.ivUserIcon.setColorFilter(Color.parseColor("#10B981"))
                }
                "CAREGIVER" -> {
                    binding.tvUserRoleBadge.setTextColor(Color.parseColor("#2563EB"))
                    binding.cardAvatar.strokeColor = Color.parseColor("#93C5FD")
                    binding.cardAvatar.setCardBackgroundColor(Color.parseColor("#EFF6FF"))
                    binding.ivUserIcon.setColorFilter(Color.parseColor("#2563EB"))
                }
                "KELUARGA" -> {
                    binding.tvUserRoleBadge.setTextColor(Color.parseColor("#8B5CF6"))
                    binding.cardAvatar.strokeColor = Color.parseColor("#C4B5FD")
                    binding.cardAvatar.setCardBackgroundColor(Color.parseColor("#F5F3FF"))
                    binding.ivUserIcon.setColorFilter(Color.parseColor("#8B5CF6"))
                }
                "PENGGUNA_BELT" -> {
                    binding.tvUserRoleBadge.setTextColor(Color.parseColor("#F59E0B"))
                    binding.cardAvatar.strokeColor = Color.parseColor("#FDE68A")
                    binding.cardAvatar.setCardBackgroundColor(Color.parseColor("#FFFBEB"))
                    binding.ivUserIcon.setColorFilter(Color.parseColor("#F59E0B"))
                }
            }

            if (user.statusAktif == false) {
                binding.tvUserRoleBadge.text = "⏳ $role • MENUNGGU ACC"
                binding.tvUserRoleBadge.setTextColor(Color.parseColor("#B45309"))
                binding.btnQuickApprove.visibility = android.view.View.VISIBLE
                binding.btnQuickApprove.setOnClickListener {
                    onApproveClick?.invoke(user)
                }
            } else {
                binding.btnQuickApprove.visibility = android.view.View.GONE
            }

            binding.cardUserItem.setOnClickListener {
                onItemClick(user)
            }
        }
    }
}
