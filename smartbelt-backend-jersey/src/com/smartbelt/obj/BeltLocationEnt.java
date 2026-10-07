package com.smartbelt.obj;

import java.io.Serializable;

// ============================================================================
// DTO MONITORING LOKASI GPS AKTIF (BeltLocationEnt)
// ============================================================================
// Objek data transfer ini dikembalikan oleh endpoint GET /api/perangkat/lokasi-aktif.
// Berisi gabungan informasi perangkat sabuk, identitas lansia pemakai, koordinat GPS
// terakhir, dan status keamanan darurat untuk dirender sebagai pin marker di Peta Android.
public class BeltLocationEnt implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer idPerangkat;            // ID perangkat di tb_perangkat
    private String namaPerangkat;           // Nama label sabuk ("Smart-Belt 01")
    private String macAddress;              // MAC Address hardware Pico W
    private Boolean statusKoneksi;          // Status online/offline (true = Online)
    private Integer statusBaterai;          // Persentase daya baterai (%)
    private Integer idPenghuni;             // ID lansia pemakai di tb_penghuni
    private String namaPenghuni;            // Nama lengkap lansia
    private String alamatKamar;             // Nomor kamar wisma panti
    private Double latitude;                // Koordinat lintang GPS terkini
    private Double longitude;               // Koordinat bujur GPS terkini
    private String waktuTerakhir;           // Timestamp telemetri terakhir diterima
    private String statusDarurat;           // Status alert (TERDETEKSI, COUNTDOWN, AMAN)
    private String jenisKejadianTerakhir;   // Jenis insiden terakhir (JATUH, SOS, dll)

    public BeltLocationEnt() {
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

    public Boolean getStatusKoneksi() {
        return statusKoneksi;
    }

    public void setStatusKoneksi(Boolean statusKoneksi) {
        this.statusKoneksi = statusKoneksi;
    }

    public Integer getStatusBaterai() {
        return statusBaterai;
    }

    public void setStatusBaterai(Integer statusBaterai) {
        this.statusBaterai = statusBaterai;
    }

    public Integer getIdPenghuni() {
        return idPenghuni;
    }

    public void setIdPenghuni(Integer idPenghuni) {
        this.idPenghuni = idPenghuni;
    }

    public String getNamaPenghuni() {
        return namaPenghuni;
    }

    public void setNamaPenghuni(String namaPenghuni) {
        this.namaPenghuni = namaPenghuni;
    }

    public String getAlamatKamar() {
        return alamatKamar;
    }

    public void setAlamatKamar(String alamatKamar) {
        this.alamatKamar = alamatKamar;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getWaktuTerakhir() {
        return waktuTerakhir;
    }

    public void setWaktuTerakhir(String waktuTerakhir) {
        this.waktuTerakhir = waktuTerakhir;
    }

    public String getStatusDarurat() {
        return statusDarurat;
    }

    public void setStatusDarurat(String statusDarurat) {
        this.statusDarurat = statusDarurat;
    }

    public String getJenisKejadianTerakhir() {
        return jenisKejadianTerakhir;
    }

    public void setJenisKejadianTerakhir(String jenisKejadianTerakhir) {
        this.jenisKejadianTerakhir = jenisKejadianTerakhir;
    }
}
