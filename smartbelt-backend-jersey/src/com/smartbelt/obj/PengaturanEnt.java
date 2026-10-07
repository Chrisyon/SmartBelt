package com.smartbelt.obj;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

// ============================================================================
// MODEL ENTITY PENGATURAN & AMBANG BATAS SABUK (PengaturanEnt)
// ============================================================================
@JsonIgnoreProperties(ignoreUnknown = true)
public class PengaturanEnt implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("idPengaturan")
    private Integer idPengaturan;           // Primary Key tabel tb_pengaturan
    
    @JsonProperty("thresholdSvm")
    private Double thresholdSvm;            // Ambang batas lonjakan percepatan SVM jatuh (contoh: 2.70g)
    
    @JsonProperty("thresholdTilt")
    private Double thresholdTilt;           // Ambang batas kemiringan badan rebah (contoh: 55.0 derajat)
    
    @JsonProperty("durasiMiringDetik")
    private Integer durasiMiringDetik;      // Durasi miring terus menerus sebelum alarm (detik, default: 5)
    
    @JsonProperty("thresholdRollCount")
    private Integer thresholdRollCount;     // Jumlah konfirmasi perputaran tubuh saat jatuh
    
    @JsonProperty("durasiCountdown")
    private Integer durasiCountdown;        // Waktu hitung mundur buzzer sebelum alarm terkirim (detik)
    
    @JsonProperty("buzzerAktif")
    private Boolean buzzerAktif;            // Saklar aktif/nonaktif buzzer pada sabuk
    
    @JsonProperty("minHeartRate")
    private Integer minHeartRate;           // Batas detak jantung terendah normal (BPM, default: 60)
    
    @JsonProperty("minSpo2")
    private Integer minSpo2;                // Batas saturasi oksigen terendah normal (%, default: 95)
    
    @JsonProperty("lokasiDefaultNama")
    private String lokasiDefaultNama;       // Nama label lokasi wisma ("Wisma Panti Lansia")
    
    @JsonProperty("defaultLatitude")
    private Double defaultLatitude;         // Titik lintang GPS pusat wisma panti
    
    @JsonProperty("defaultLongitude")
    private Double defaultLongitude;        // Titik bujur GPS pusat wisma panti
    
    @JsonProperty("maxDistanceMeters")
    private Integer maxDistanceMeters;      // Radius batas aman Geofencing dari titik wisma (meter)
    
    @JsonProperty("terakhirDiperbarui")
    private String terakhirDiperbarui;      // Waktu perubahan konfigurasi terakhir

    public PengaturanEnt() {
    }

    public Integer getIdPengaturan() {
        return idPengaturan;
    }

    public void setIdPengaturan(Integer idPengaturan) {
        this.idPengaturan = idPengaturan;
    }

    public Double getThresholdSvm() {
        return thresholdSvm;
    }

    public void setThresholdSvm(Double thresholdSvm) {
        this.thresholdSvm = thresholdSvm;
    }

    public Double getThresholdTilt() {
        return thresholdTilt;
    }

    public void setThresholdTilt(Double thresholdTilt) {
        this.thresholdTilt = thresholdTilt;
    }

    public Integer getDurasiMiringDetik() {
        return durasiMiringDetik;
    }

    public void setDurasiMiringDetik(Integer durasiMiringDetik) {
        this.durasiMiringDetik = durasiMiringDetik;
    }

    public Integer getThresholdRollCount() {
        return thresholdRollCount;
    }

    public void setThresholdRollCount(Integer thresholdRollCount) {
        this.thresholdRollCount = thresholdRollCount;
    }

    public Integer getDurasiCountdown() {
        return durasiCountdown;
    }

    public void setDurasiCountdown(Integer durasiCountdown) {
        this.durasiCountdown = durasiCountdown;
    }

    public Boolean getBuzzerAktif() {
        return buzzerAktif;
    }

    public void setBuzzerAktif(Boolean buzzerAktif) {
        this.buzzerAktif = buzzerAktif;
    }

    public Integer getMinHeartRate() {
        return minHeartRate;
    }

    public void setMinHeartRate(Integer minHeartRate) {
        this.minHeartRate = minHeartRate;
    }

    public Integer getMinSpo2() {
        return minSpo2;
    }

    public void setMinSpo2(Integer minSpo2) {
        this.minSpo2 = minSpo2;
    }

    public String getLokasiDefaultNama() {
        return lokasiDefaultNama;
    }

    public void setLokasiDefaultNama(String lokasiDefaultNama) {
        this.lokasiDefaultNama = lokasiDefaultNama;
    }

    public Double getDefaultLatitude() {
        return defaultLatitude;
    }

    public void setDefaultLatitude(Double defaultLatitude) {
        this.defaultLatitude = defaultLatitude;
    }

    public Double getDefaultLongitude() {
        return defaultLongitude;
    }

    public void setDefaultLongitude(Double defaultLongitude) {
        this.defaultLongitude = defaultLongitude;
    }

    public Integer getMaxDistanceMeters() {
        return maxDistanceMeters;
    }

    public void setMaxDistanceMeters(Integer maxDistanceMeters) {
        this.maxDistanceMeters = maxDistanceMeters;
    }

    public String getTerakhirDiperbarui() {
        return terakhirDiperbarui;
    }

    public void setTerakhirDiperbarui(String terakhirDiperbarui) {
        this.terakhirDiperbarui = terakhirDiperbarui;
    }
}
