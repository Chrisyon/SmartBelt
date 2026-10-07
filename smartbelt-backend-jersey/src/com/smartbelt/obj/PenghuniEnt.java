package com.smartbelt.obj;

import java.io.Serializable;

// ============================================================================
// MODEL ENTITY PENGHUNI WISMA / LANSIA (PenghuniEnt)
// ============================================================================
// Merepresentasikan data profil lansia pada tabel 'tb_penghuni'.
// Menyimpan data medis dasar (nama, tgl lahir, jenis kelamin, kamar wisma,
// nomor telepon, dan riwayat penyakit bawaan), relasi akun pengguna (tb_user),
// serta perangkat sabuk pintar Smart-Belt (tb_perangkat) yang sedang dikenakan.
public class PenghuniEnt implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer idPenghuni;         // Primary Key tabel tb_penghuni
    private String namaLengkap;         // Nama lengkap lansia penghuni wisma
    private String tanggalLahir;        // Tanggal lahir (YYYY-MM-DD) untuk kalkulasi usia
    private String jenisKelamin;        // 'L' (Laki-laki) atau 'P' (Perempuan)
    private String alamatKamar;         // Nomor atau blok kamar tidur di panti
    private String noTelepon;           // Nomor telepon pribadi lansia (jika ada)
    private String riwayatPenyakit;     // Catatan diagnosa medis (Hipertensi, Stroke, Diabetes, dll)
    private String fotoProfil;          // Nama berkas gambar profil foto lansia
    private Boolean statusAktif;        // Status aktif tinggal di panti (true = Masih aktif)
    private Integer idUser;             // ID akun pengguna keluarga penanggung jawab
    private Integer idUserLansia;       // ID akun login lansia sendiri (jika lansia memiliki HP)
    private Integer idPerangkat;        // ID perangkat sabuk pintar yang dipasangkan
    private String namaPerangkat;       // Nama label sabuk IoT yang terpasang
    private String macAddress;          // MAC address sabuk IoT yang terpasang

    public PenghuniEnt() {
    }

    public Integer getIdUser() {
        return idUser;
    }

    public void setIdUser(Integer idUser) {
        this.idUser = idUser;
    }

    public Integer getIdUserLansia() {
        return idUserLansia;
    }

    public void setIdUserLansia(Integer idUserLansia) {
        this.idUserLansia = idUserLansia;
    }

    public Integer getIdPerangkat() {
        return idPerangkat;
    }

    public void setIdPerangkat(Integer idPerangkat) {
        this.idPerangkat = idPerangkat;
    }

    public String getNamaPerangkat() {
        return namaPerangkat;
    }

    public void setNamaPerangkat(String namaPerangkat) {
        this.namaPerangkat = namaPerangkat;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public Integer getIdPenghuni() {
        return idPenghuni;
    }

    public void setIdPenghuni(Integer idPenghuni) {
        this.idPenghuni = idPenghuni;
    }

    public String getNamaLengkap() {
        return namaLengkap;
    }

    public void setNamaLengkap(String namaLengkap) {
        this.namaLengkap = namaLengkap;
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

    public String getNoTelepon() {
        return noTelepon;
    }

    public void setNoTelepon(String noTelepon) {
        this.noTelepon = noTelepon;
    }

    public String getRiwayatPenyakit() {
        return riwayatPenyakit;
    }

    public void setRiwayatPenyakit(String riwayatPenyakit) {
        this.riwayatPenyakit = riwayatPenyakit;
    }

    public String getFotoProfil() {
        return fotoProfil;
    }

    public void setFotoProfil(String fotoProfil) {
        this.fotoProfil = fotoProfil;
    }

    public Boolean getStatusAktif() {
        return statusAktif;
    }

    public void setStatusAktif(Boolean statusAktif) {
        this.statusAktif = statusAktif;
    }
}
