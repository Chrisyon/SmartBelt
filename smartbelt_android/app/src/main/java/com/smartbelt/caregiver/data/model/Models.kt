package com.smartbelt.caregiver.data.model

//datatransferobject (DTO)
//dataclassnya disini

//Menyimpan bentuk data (misal: data Lansia punya nama, umur, kamar, dan riwayat penyakit).
//Menampung data (tempat menyimpan data yang didapat dari server sebelum ditampilkan ke layar HP).

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: T?
)

data class User(
    @SerializedName("idUser") val idUser: Int? = null,
    @SerializedName("namaLengkap") val namaLengkap: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("noTelepon") val noTelepon: String? = null,
    @SerializedName("fotoProfil") val fotoProfil: String? = null,
    @SerializedName("emailVerified") val emailVerified: Boolean? = null,
    @SerializedName("statusAktif") val statusAktif: Boolean? = null,
    @SerializedName("passwordHash") val passwordHash: String? = null,
    @SerializedName("telegramChatId") val telegramChatId: String? = null
) : Serializable

data class AuthResponse(
    @SerializedName("token") val token: String?,
    @SerializedName("tokenType") val tokenType: String?,
    @SerializedName("idUser") val idUser: Int?,
    @SerializedName("username") val username: String?,
    @SerializedName("email") val email: String?,
    @SerializedName("namaLengkap") val namaLengkap: String?,
    @SerializedName("role") val role: String?,
    @SerializedName("fotoProfil") val fotoProfil: String?
)

data class RegisterRequest(
    @SerializedName("namaLengkap") val namaLengkap: String,
    @SerializedName("email") val email: String,
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String,
    @SerializedName("role") val role: String = "KELUARGA",
    @SerializedName("noTelepon") val noTelepon: String? = null,
    @SerializedName("telegramChatId") val telegramChatId: String? = null,
    @SerializedName("idPenghuniTarget") val idPenghuniTarget: Int? = null,
    @SerializedName("hubunganKeluarga") val hubunganKeluarga: String? = null,
    @SerializedName("tanggalLahir") val tanggalLahir: String? = null,
    @SerializedName("jenisKelamin") val jenisKelamin: String? = null,
    @SerializedName("alamatKamar") val alamatKamar: String? = null,
    @SerializedName("riwayatPenyakit") val riwayatPenyakit: String? = null
)

data class CreateUserRequest(
    @SerializedName("namaLengkap") val namaLengkap: String,
    @SerializedName("email") val email: String,
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String? = "123456",
    @SerializedName("role") val role: String = "KELUARGA",
    @SerializedName("noTelepon") val noTelepon: String? = null,
    @SerializedName("idPenghuni") val idPenghuni: Int? = null,
    @SerializedName("idPenghuniTarget") val idPenghuniTarget: Int? = null,
    @SerializedName("hubunganKeluarga") val hubunganKeluarga: String? = null,
    @SerializedName("alamatKamar") val alamatKamar: String? = null,
    @SerializedName("tanggalLahir") val tanggalLahir: String? = null,
    @SerializedName("jenisKelamin") val jenisKelamin: String? = null,
    @SerializedName("riwayatPenyakit") val riwayatPenyakit: String? = null,
    @SerializedName("telegramChatId") val telegramChatId: String? = null
)

data class LoginRequest(
    val usernameOrEmail: String,
    val password: String,
    val deviceInfo: String? = "Android Caregiver App"
)

data class ForgotPasswordRequest(
    val usernameOrEmail: String
)

data class ResetPasswordRequest(
    val token: String,
    val newPassword: String
)

data class Penghuni(
    @SerializedName("idPenghuni") val idPenghuni: Int? = null,
    @SerializedName("idUser") val idUser: Int? = null,
    @SerializedName("idUserLansia") val idUserLansia: Int? = null,
    @SerializedName("namaLengkap") val namaLengkap: String? = null,
    @SerializedName("tanggalLahir") val tanggalLahir: String? = null,
    @SerializedName("jenisKelamin") val jenisKelamin: String? = null,
    @SerializedName("alamatKamar") val alamatKamar: String? = null,
    @SerializedName("noTelepon") val noTelepon: String? = null,
    @SerializedName("riwayatPenyakit") val riwayatPenyakit: String? = null,
    @SerializedName("fotoProfil") val fotoProfil: String? = null,
    @SerializedName("statusAktif") val statusAktif: Boolean? = true,
    @SerializedName("idPerangkat") val idPerangkat: Int? = null,
    @SerializedName("namaPerangkat") val namaPerangkat: String? = null,
    @SerializedName("macAddress") val macAddress: String? = null
) : Serializable

data class KeluargaRelation(
    @SerializedName("idPenghuni") val idPenghuni: Int? = null,
    @SerializedName("namaLengkap") val namaLengkap: String? = null,
    @SerializedName("alamatKamar") val alamatKamar: String? = null,
    @SerializedName("hubungan") val hubungan: String? = null
)

data class KontakDarurat(
    @SerializedName("idKontak") val idKontak: Int?,
    @SerializedName("namaKontak") val namaKontak: String?,
    @SerializedName("noTelepon") val noTelepon: String?,
    @SerializedName("hubungan") val hubungan: String?,
    @SerializedName("prioritas") val prioritas: Int?,
    @SerializedName("statusAktif") val statusAktif: Boolean?
) : Serializable

data class Perangkat(
    @SerializedName("idPerangkat") val idPerangkat: Int?,
    @SerializedName("namaPerangkat") val namaPerangkat: String?,
    @SerializedName("macAddress") val macAddress: String?,
    @SerializedName("ipAddress") val ipAddress: String?,
    @SerializedName("versiFirmware") val versiFirmware: String?,
    @SerializedName("statusBaterai") val statusBaterai: Int?,
    @SerializedName("statusKoneksi") val statusKoneksi: Boolean?,
    @SerializedName("terakhirTerhubung") val terakhirTerhubung: String?
) : Serializable

data class KejadianDarurat(
    @SerializedName("idKejadian") val idKejadian: Int? = null,
    @SerializedName("penghuni") val penghuni: Penghuni? = null,
    @SerializedName("perangkat") val perangkat: Perangkat? = null,
    @SerializedName("jenisKejadian") val jenisKejadian: String? = null,
    @SerializedName("svmValue") val svmValue: Double? = null,
    @SerializedName("pitchValue") val pitchValue: Double? = null,
    @SerializedName("rollValue") val rollValue: Double? = null,
    @SerializedName("accelX") val accelX: Double? = null,
    @SerializedName("accelY") val accelY: Double? = null,
    @SerializedName("accelZ") val accelZ: Double? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("alamatLokasi") val alamatLokasi: String? = null,
    @SerializedName("statusKejadian") val statusKejadian: String? = null,
    @SerializedName("waktuKejadian") val waktuKejadian: String? = null,
    @SerializedName("waktuRespon") val waktuRespon: String? = null,
    @SerializedName("catatan") val catatan: String? = null,
    @SerializedName("durasiCountdown") val durasiCountdown: Int? = null
) : Serializable

data class DataSensor(
    @SerializedName("idData") val idData: Long?,
    @SerializedName("accelX") val accelX: Double?,
    @SerializedName("accelY") val accelY: Double?,
    @SerializedName("accelZ") val accelZ: Double?,
    @SerializedName("gyroX") val gyroX: Double?,
    @SerializedName("gyroY") val gyroY: Double?,
    @SerializedName("gyroZ") val gyroZ: Double?,
    @SerializedName("svm") val svm: Double?,
    @SerializedName("pitch") val pitch: Double?,
    @SerializedName("roll") val roll: Double?,
    @SerializedName("heartRate") val heartRate: Int?,
    @SerializedName("spo2") val spo2: Int?,
    @SerializedName("latitude") val latitude: Double?,
    @SerializedName("longitude") val longitude: Double?,
    @SerializedName("waktuBaca") val waktuBaca: String?
) : Serializable

data class EmergencyRequest(
    val macAddress: String,
    val jenisKejadian: String = "JATUH",
    val statusKejadian: String = "AUTO_CALL",
    val svmValue: Double? = 3.5,
    val pitchValue: Double? = null,
    val rollValue: Double? = null,
    val latitude: Double? = -3.6951234,
    val longitude: Double? = 128.1834567,
    val alamatLokasi: String? = "Wisma Panti INAKAKA Ambon"
)

data class Pengaturan(
    @SerializedName("idPengaturan") val idPengaturan: Int? = null,
    @SerializedName("thresholdSvm") val thresholdSvm: Double? = 2.70,
    @SerializedName("thresholdTilt") val thresholdTilt: Double? = 55.0,
    @SerializedName("durasiMiringDetik") val durasiMiringDetik: Int? = 5,
    @SerializedName("thresholdRollCount") val thresholdRollCount: Int? = 5,
    @SerializedName("durasiCountdown") val durasiCountdown: Int? = 10,
    @SerializedName("buzzerAktif") val buzzerAktif: Boolean? = true,
    @SerializedName("minHeartRate") val minHeartRate: Int? = 60,
    @SerializedName("maxHeartRate") val maxHeartRate: Int? = 120,
    @SerializedName("minSpo2") val minSpo2: Int? = 95,
    @SerializedName("lokasiDefaultNama") val lokasiDefaultNama: String? = "Wisma Panti INAKAKA Ambon",
    @SerializedName("defaultLatitude") val defaultLatitude: Double? = -3.695436,
    @SerializedName("defaultLongitude") val defaultLongitude: Double? = 128.181427,
    @SerializedName("maxDistanceMeters") val maxDistanceMeters: Int? = 100
) : Serializable

data class UpdatePengaturanRequest(
    @SerializedName("thresholdSvm") val thresholdSvm: Double?,
    @SerializedName("thresholdRollCount") val thresholdRollCount: Int?,
    @SerializedName("durasiMiringDetik") val durasiMiringDetik: Int? = 5,
    @SerializedName("durasiCountdown") val durasiCountdown: Int?,
    @SerializedName("buzzerAktif") val buzzerAktif: Boolean? = true,
    @SerializedName("minHeartRate") val minHeartRate: Int? = 60,
    @SerializedName("maxHeartRate") val maxHeartRate: Int? = 120,
    @SerializedName("minSpo2") val minSpo2: Int? = 95,
    @SerializedName("lokasiDefaultNama") val lokasiDefaultNama: String? = "Wisma Panti INAKAKA Ambon",
    @SerializedName("defaultLatitude") val defaultLatitude: Double? = -3.695436,
    @SerializedName("defaultLongitude") val defaultLongitude: Double? = 128.181427,
    @SerializedName("maxDistanceMeters") val maxDistanceMeters: Int? = 100
)

data class BeltLocation(
    @SerializedName("idPerangkat") val idPerangkat: Int?,
    @SerializedName("namaPerangkat") val namaPerangkat: String?,
    @SerializedName("macAddress") val macAddress: String?,
    @SerializedName("statusKoneksi") val statusKoneksi: Boolean?,
    @SerializedName("statusBaterai") val statusBaterai: Int?,
    @SerializedName("idPenghuni") val idPenghuni: Int?,
    @SerializedName("namaPenghuni") val namaPenghuni: String?,
    @SerializedName("alamatKamar") val alamatKamar: String?,
    @SerializedName("latitude") var latitude: Double?,
    @SerializedName("longitude") var longitude: Double?,
    @SerializedName("waktuTerakhir") val waktuTerakhir: String?,
    @SerializedName("statusDarurat") val statusDarurat: String?,
    @SerializedName("jenisKejadianTerakhir") val jenisKejadianTerakhir: String?
) : Serializable

