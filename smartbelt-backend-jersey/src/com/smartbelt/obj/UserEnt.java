package com.smartbelt.obj;

import java.io.Serializable;

// ============================================================================
// MODEL ENTITY PENGGUNA SISTEM (UserEnt)
// ============================================================================
// Merepresentasikan akun pengguna pada tabel 'tb_user'.
// Mendukung 4 jenis Role:
// - ADMIN          : Pengelola penuh sistem panti wisma lansia
// - CAREGIVER      : Perawat pemantau harian & penangan insiden darurat
// - KELUARGA       : Kerabat lansia yang memantau kondisi anggota keluarganya
// - PENGGUNA_BELT  : Lansia pemakai sabuk pintar
// Kelas ini juga merangkap DTO saat registrasi formulir gabungan (user + profil lansia).
public class UserEnt implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer idUser;             // Primary Key tabel tb_user
    private String namaLengkap;         // Nama lengkap pengguna
    private String email;               // Alamat surel (wajib unik)
    private String username;            // Username login (wajib unik)
    private String password;            // Plain text saat input, null saat response
    private String role;                // ADMIN, CAREGIVER, KELUARGA, PENGGUNA_BELT
    private String noTelepon;           // Nomor kontak WhatsApp / Telepon
    private String fotoProfil;          // Nama berkas avatar profil
    private Boolean emailVerified;      // Status verifikasi email
    private Boolean statusAktif;        // Status persetujuan akun (1 = Aktif, 0 = Menunggu ACC Admin)
    private String telegramChatId;      // ID Chat Telegram untuk notifikasi bot
    
    // Atribut pendukung form pendaftaran (Keluarga / Lansia)
    private Integer idPenghuniTarget;   // ID lansia yang dipilih jika mendaftar sebagai KELUARGA
    private String hubunganKeluarga;    // Hubungan keluarga (ANAK, CUCU, SUAMI, ISTRI)
    private String tanggalLahir;        // Tgl lahir lansia jika mendaftar sebagai LANSIA
    private String jenisKelamin;        // Jenis kelamin lansia ('L'/'P')
    private String alamatKamar;         // Nomor kamar wisma lansia
    private String riwayatPenyakit;     // Riwayat penyakit bawaan lansia

    public Integer getIdPenghuniTarget() {
        return idPenghuniTarget;
    }

    public void setIdPenghuniTarget(Integer idPenghuniTarget) {
        this.idPenghuniTarget = idPenghuniTarget;
    }

    public String getHubunganKeluarga() {
        return hubunganKeluarga;
    }

    public void setHubunganKeluarga(String hubunganKeluarga) {
        this.hubunganKeluarga = hubunganKeluarga;
    }

    public String getTanggalLahir() {
        return tanggalLahir;
    }

    public void setTanggalLahir(String tanggalLahir) {
        this.tanggalLahir = tanggalLahir;
    }

    public String getJenisKelamin() {
        return jenisKelamin;
    }

    public void setJenisKelamin(String jenisKelamin) {
        this.jenisKelamin = jenisKelamin;
    }

    public String getAlamatKamar() {
        return alamatKamar;
    }

    public void setAlamatKamar(String alamatKamar) {
        this.alamatKamar = alamatKamar;
    }

    public String getRiwayatPenyakit() {
        return riwayatPenyakit;
    }

    public void setRiwayatPenyakit(String riwayatPenyakit) {
        this.riwayatPenyakit = riwayatPenyakit;
    }

    public UserEnt() {
    }

    public Integer getIdUser() {
        return idUser;
    }

    public void setIdUser(Integer idUser) {
        this.idUser = idUser;
    }

    public String getNamaLengkap() {
        return namaLengkap;
    }

    public void setNamaLengkap(String namaLengkap) {
        this.namaLengkap = namaLengkap;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getNoTelepon() {
        return noTelepon;
    }

    public void setNoTelepon(String noTelepon) {
        this.noTelepon = noTelepon;
    }

    public String getFotoProfil() {
        return fotoProfil;
    }

    public void setFotoProfil(String fotoProfil) {
        this.fotoProfil = fotoProfil;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public Boolean getStatusAktif() {
        return statusAktif;
    }

    public void setStatusAktif(Boolean statusAktif) {
        this.statusAktif = statusAktif;
    }

    public String getTelegramChatId() {
        return telegramChatId;
    }

    public void setTelegramChatId(String telegramChatId) {
        this.telegramChatId = telegramChatId;
    }
}
