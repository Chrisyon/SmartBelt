package com.smartbelt.caregiver.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.smartbelt.caregiver.data.pref.SessionManager
import com.smartbelt.caregiver.databinding.ActivitySettingsMainBinding

class SettingsMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsMainBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        binding.btnBack.setOnClickListener { finish() }

        // Role-based Access: Pengaturan Sabuk HANYA untuk Pengguna (Lansia) dan Keluarga
        val currentRole = sessionManager.getRole()?.uppercase() ?: "KELUARGA"
        val isAllowedBeltSettings = currentRole == "KELUARGA" || currentRole == "PENGGUNA_BELT" || currentRole == "LANSIA"

        if (isAllowedBeltSettings) {
            binding.tvHeaderDevice.visibility = android.view.View.VISIBLE
            binding.cardDeviceSettings.visibility = android.view.View.VISIBLE
        } else {
            binding.tvHeaderDevice.visibility = android.view.View.GONE
            binding.cardDeviceSettings.visibility = android.view.View.GONE
        }

        // Navigate to Device Settings
        binding.cardDeviceSettings.setOnClickListener {
            val intent = Intent(this, DeviceSettingsActivity::class.java)
            startActivity(intent)
        }

        // Navigate to Emergency Contacts CRUD
        binding.cardEmergencyContacts.setOnClickListener {
            val intent = Intent(this, com.smartbelt.caregiver.ui.contact.EmergencyContactsActivity::class.java)
            startActivity(intent)
        }

        // Navigate to Account Settings
        binding.cardAccountSettings.setOnClickListener {
            val intent = Intent(this, AccountSettingsActivity::class.java)
            startActivity(intent)
        }

        // Dark Mode Toggle
        binding.switchDarkMode.isChecked = sessionManager.isDarkMode()
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            sessionManager.setDarkMode(isChecked)
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                Toast.makeText(this, "Mode Gelap diaktifkan", Toast.LENGTH_SHORT).show()
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                Toast.makeText(this, "Mode Terang diaktifkan", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
