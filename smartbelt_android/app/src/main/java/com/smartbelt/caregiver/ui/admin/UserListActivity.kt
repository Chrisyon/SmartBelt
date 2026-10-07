package com.smartbelt.caregiver.ui.admin

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.smartbelt.caregiver.R
import com.smartbelt.caregiver.data.api.ApiClient
import com.smartbelt.caregiver.data.model.User
import com.smartbelt.caregiver.databinding.ActivityUserListBinding
import kotlinx.coroutines.launch

class UserListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserListBinding
    private lateinit var adapter: UserAdapter
    private var allUsers = listOf<User>()
    private var currentFilterRole: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        setupRecyclerView()
        setupChips()

        binding.swipeRefresh.setOnRefreshListener {
            loadUsers()
        }

        binding.fabAddUser.setOnClickListener {
            val intent = Intent(this, UserCreateActivity::class.java)
            startActivity(intent)
        }

        loadUsers()
    }

    override fun onResume() {
        super.onResume()
        loadUsers()
    }

    private fun setupRecyclerView() {
        adapter = UserAdapter(
            onItemClick = { user ->
                val intent = Intent(this, UserEditActivity::class.java).apply {
                    putExtra("MODE", "EDIT")
                    putExtra("USER_ID", user.idUser)
                    putExtra("NAMA_LENGKAP", user.namaLengkap)
                    putExtra("USERNAME", user.username)
                    putExtra("EMAIL", user.email)
                    putExtra("NO_TELEPON", user.noTelepon)
                    putExtra("TELEGRAM_CHAT_ID", user.telegramChatId)
                    putExtra("ROLE", user.role)
                    putExtra("STATUS_AKTIF", user.statusAktif ?: true)
                }
                startActivity(intent)
            },
            onApproveClick = { user ->
                confirmApproveUser(user)
            }
        )

        binding.rvUsers.layoutManager = LinearLayoutManager(this)
        binding.rvUsers.adapter = adapter
    }

    private fun confirmApproveUser(user: User) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Setujui Akun (ACC)")
            .setMessage("Apakah Anda yakin ingin menyetujui akun \"${user.namaLengkap ?: user.username}\" (${user.role}) agar dapat login?")
            .setPositiveButton("Setujui (ACC)") { _, _ ->
                approveUser(user)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun approveUser(user: User) {
        val userId = user.idUser ?: return
        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(this@UserListActivity)
                val res = api.approveUser(userId)
                if (res.isSuccessful && res.body()?.success == true) {
                    Toast.makeText(this@UserListActivity, "Akun ${user.username} berhasil di-ACC (Aktif)!", Toast.LENGTH_SHORT).show()
                    loadUsers()
                } else {
                    Toast.makeText(this@UserListActivity, res.body()?.message ?: "Gagal menyetujui akun", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@UserListActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupChips() {
        binding.chipGroupRole.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            currentFilterRole = when (checkedIds.first()) {
                R.id.chipPending -> "PENDING"
                R.id.chipCaregiver -> "CAREGIVER"
                R.id.chipKeluarga -> "KELUARGA"
                R.id.chipPenggunaBelt -> "PENGGUNA_BELT"
                R.id.chipAdmin -> "ADMIN"
                else -> null
            }
            applyFilter()
        }
    }

    private fun loadUsers() {
        binding.swipeRefresh.isRefreshing = true

        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(this@UserListActivity)
                val res = api.getAllUsers()

                binding.swipeRefresh.isRefreshing = false

                if (res.isSuccessful && res.body()?.success == true) {
                    allUsers = res.body()?.data ?: emptyList()
                    applyFilter()
                } else {
                    Toast.makeText(this@UserListActivity, res.body()?.message ?: "Gagal memuat data", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(this@UserListActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun applyFilter() {
        val filtered = when (currentFilterRole) {
            null -> allUsers
            "PENDING" -> allUsers.filter { it.statusAktif == false }
            else -> allUsers.filter { it.role.equals(currentFilterRole, ignoreCase = true) }
        }
        adapter.submitList(filtered)
    }
}
