package com.smartbelt.obj;

import java.io.Serializable;

// ============================================================================
// DTO PAYLOAD DARURAT JATUH & SOS (EmergencyRequestEnt)
// ============================================================================
// Objek data transfer ini dikirim seketika oleh Raspberry Pi Pico W saat mendeteksi
// insiden jatuh (SVM > ambang batas + miring) atau saat tombol darurat (SOS) ditekan.
// Dikirim via HTTP POST ke /api/iot/emergency untuk membunyikan alarm dan mengirim
// notifikasi Telegram instan ke pihak perawat panti.
public class EmergencyRequestEnt implements Serializable {
    private static final long serialVersionUID = 1L;

    private String macAddress;                  // MAC Address sabuk pelapor darurat
    private String jenisKejadian = "JATUH";     // JATUH, PANIC_BUTTON, TILT_EKSTREM, KELUAR_ZONA_AMAN, dll.
    private String statusKejadian = "MENUNGGU"; // Status awal kejadian: MENUNGGU / COUNTDOWN / TERDETEKSI
    private Double svmValue;                    // Puncak nilai SVM yang memicu jatuh (g)
    private Double pitchValue;                  // Sudut kemiringan badan saat insiden (derajat)
    private Double rollValue;                   // Sudut kemiringan badan saat insiden (derajat)
    private Double accelX;                      // Vektor percepatan X saat benturan
    private Double accelY;                      // Vektor percepatan Y saat benturan
    private Double accelZ;                      // Vektor percepatan Z saat benturan
    private Double latitude;                    // Titik lintang GPS lokasi insiden
    private Double longitude;                   // Titik bujur GPS lokasi insiden
    private String alamatLokasi;                // Keterangan teks lokasi wisma/kamar

    public EmergencyRequestEnt() {
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getJenisKejadian() {
        return jenisKejadian;
    }

    public void setJenisKejadian(String jenisKejadian) {
        this.jenisKejadian = jenisKejadian;
    }

    public String getStatusKejadian() {
        return statusKejadian;
    }

    public void setStatusKejadian(String statusKejadian) {
        this.statusKejadian = statusKejadian;
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
}
