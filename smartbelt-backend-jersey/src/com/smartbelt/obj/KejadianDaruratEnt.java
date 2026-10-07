package com.smartbelt.obj;

import java.io.Serializable;

// ============================================================================
// MODEL ENTITY KEJADIAN DARURAT (KejadianDaruratEnt)
// ============================================================================
// Merepresentasikan data log insiden darurat pada tabel 'tb_kejadian_darurat'.
// Menyimpan riwayat kejadian darurat (JATUH, PANIC_BUTTON/SOS, TILT_EKSTREM,
// KELUAR_ZONA_AMAN, dll.) lengkap dengan data telemetri saat kejadian, koordinat GPS,
// dan status tindak lanjut perawat (status_kejadian, waktu_respon, catatan tindakan).
public class KejadianDaruratEnt implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer idKejadian;         // Primary Key tabel tb_kejadian_darurat
    private PenghuniEnt penghuni;       // Relasi objek lansia yang mengalami insiden
    private PerangkatEnt perangkat;     // Relasi objek perangkat sabuk yang mendeteksi
    private String jenisKejadian;       // JATUH, PANIC_BUTTON, TILT_EKSTREM, KELUAR_ZONA_AMAN
    private Double svmValue;            // Nilai SVM benturan saat jatuh (g)
    private Double pitchValue;          // Nilai pitch kemiringan saat jatuh (derajat)
    private Double rollValue;           // Nilai roll kemiringan saat jatuh (derajat)
    private Double accelX;              // Akselerasi X saat jatuh
    private Double accelY;              // Akselerasi Y saat jatuh
    private Double accelZ;              // Akselerasi Z saat jatuh
    private Double latitude;            // Titik lintang GPS lokasi kejadian
    private Double longitude;           // Titik bujur GPS lokasi kejadian
    private String alamatLokasi;        // Keterangan lokasi (kamar / wisma panti)
    private String statusKejadian;      // TERDETEKSI, COUNTDOWN, AUTO_CALL, SELESAI, DIBATALKAN
    private String waktuKejadian;       // Waktu saat insiden pertama kali terdeteksi
    private String waktuRespon;         // Waktu saat perawat menekan tombol penanganan
    private String catatan;             // Catatan tindakan medis / penanganan oleh caregiver
    private Integer durasiCountdown;    // Lama countdown buzzer sebelum eskalasi telepon

    public KejadianDaruratEnt() {
    }

    public Integer getIdKejadian() {
        return idKejadian;
    }

    public void setIdKejadian(Integer idKejadian) {
        this.idKejadian = idKejadian;
    }

    public PenghuniEnt getPenghuni() {
        return penghuni;
    }

    public void setPenghuni(PenghuniEnt penghuni) {
        this.penghuni = penghuni;
    }

    public PerangkatEnt getPerangkat() {
        return perangkat;
    }

    public void setPerangkat(PerangkatEnt perangkat) {
        this.perangkat = perangkat;
    }

    public String getJenisKejadian() {
        return jenisKejadian;
    }

    public void setJenisKejadian(String jenisKejadian) {
        this.jenisKejadian = jenisKejadian;
    }

    public Double getSvmValue() {
        return svmValue;
    }

    public void setSvmValue(Double svmValue) {
        this.svmValue = svmValue;
    }

    public Double getPitchValue() {
        return pitchValue;
    }

    public void setPitchValue(Double pitchValue) {
        this.pitchValue = pitchValue;
    }

    public Double getRollValue() {
        return rollValue;
    }

    public void setRollValue(Double rollValue) {
        this.rollValue = rollValue;
    }

    public Double getAccelX() {
        return accelX;
    }

    public void setAccelX(Double accelX) {
        this.accelX = accelX;
    }

    public Double getAccelY() {
        return accelY;
    }

    public void setAccelY(Double accelY) {
        this.accelY = accelY;
    }

    public Double getAccelZ() {
        return accelZ;
    }

    public void setAccelZ(Double accelZ) {
        this.accelZ = accelZ;
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

    public String getAlamatLokasi() {
        return alamatLokasi;
    }

    public void setAlamatLokasi(String alamatLokasi) {
        this.alamatLokasi = alamatLokasi;
    }

    public String getStatusKejadian() {
        return statusKejadian;
    }

    public void setStatusKejadian(String statusKejadian) {
        this.statusKejadian = statusKejadian;
    }

    public String getWaktuKejadian() {
        return waktuKejadian;
    }

    public void setWaktuKejadian(String waktuKejadian) {
        this.waktuKejadian = waktuKejadian;
    }

    public String getWaktuRespon() {
        return waktuRespon;
    }

    public void setWaktuRespon(String waktuRespon) {
        this.waktuRespon = waktuRespon;
    }

    public String getCatatan() {
        return catatan;
    }

    public void setCatatan(String catatan) {
        this.catatan = catatan;
    }

    public Integer getDurasiCountdown() {
        return durasiCountdown;
    }

    public void setDurasiCountdown(Integer durasiCountdown) {
        this.durasiCountdown = durasiCountdown;
    }
}
