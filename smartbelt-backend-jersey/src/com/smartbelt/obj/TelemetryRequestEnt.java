package com.smartbelt.obj;

import java.io.Serializable;

// ============================================================================
// DTO PAYLOAD TELEMETRI IOT (TelemetryRequestEnt)
// ============================================================================
// Objek data transfer ini dikirim secara berkala (tiap 5-10 detik) oleh mikrokontroler
// Raspberry Pi Pico W melalui HTTP POST ke endpoint /api/iot/telemetry.
// Membawa seluruh hasil pembacaan sensor fisik yang tertanam pada sabuk pintar.
public class TelemetryRequestEnt implements Serializable {
    private static final long serialVersionUID = 1L;

    private String macAddress;      // MAC Address perangkat sabuk pengirim data
    private Double accelX;          // Percepatan sumbu X (g) dari MPU6050
    private Double accelY;          // Percepatan sumbu Y (g) dari MPU6050
    private Double accelZ;          // Percepatan sumbu Z (g) dari MPU6050
    private Double gyroX;           // Kecepatan sudut sumbu X (deg/s) dari MPU6050
    private Double gyroY;           // Kecepatan sudut sumbu Y (deg/s) dari MPU6050
    private Double gyroZ;           // Kecepatan sudut sumbu Z (deg/s) dari MPU6050
    private Double svm;             // Signal Vector Magnitude: sqrt(ax^2 + ay^2 + az^2)
    private Double pitch;           // Sudut kemiringan badan depan-belakang (derajat)
    private Double roll;            // Sudut kemiringan badan samping kiri-kanan (derajat)
    private Integer heartRate;      // Detak jantung (BPM) dari sensor MAX30102
    private Integer spo2;           // Saturasi oksigen darah (%) dari sensor MAX30102
    private Double latitude;        // Titik lintang GPS dari modul NEO-6M
    private Double longitude;       // Titik bujur GPS dari modul NEO-6M
    private Integer statusBaterai;  // Level persentase baterai sabuk (%)

    public TelemetryRequestEnt() {
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
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

    public Double getGyroX() {
        return gyroX;
    }

    public void setGyroX(Double gyroX) {
        this.gyroX = gyroX;
    }

    public Double getGyroY() {
        return gyroY;
    }

    public void setGyroY(Double gyroY) {
        this.gyroY = gyroY;
    }

    public Double getGyroZ() {
        return gyroZ;
    }

    public void setGyroZ(Double gyroZ) {
        this.gyroZ = gyroZ;
    }

    public Double getSvm() {
        return svm;
    }

    public void setSvm(Double svm) {
        this.svm = svm;
    }

    public Double getPitch() {
        return pitch;
    }

    public void setPitch(Double pitch) {
        this.pitch = pitch;
    }

    public Double getRoll() {
        return roll;
    }

    public void setRoll(Double roll) {
        this.roll = roll;
    }

    public Integer getHeartRate() {
        return heartRate;
    }

    public void setHeartRate(Integer heartRate) {
        this.heartRate = heartRate;
    }

    public Integer getSpo2() {
        return spo2;
    }

    public void setSpo2(Integer spo2) {
        this.spo2 = spo2;
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

    public Integer getStatusBaterai() {
        return statusBaterai;
    }

    public void setStatusBaterai(Integer statusBaterai) {
        this.statusBaterai = statusBaterai;
    }
}
