package com.smartbelt.obj;

import java.io.Serializable;

// ============================================================================
// MODEL ENTITY RIWAYAT TELEMETRI SENSOR (DataSensorEnt)
// ============================================================================
// Merepresentasikan catatan historis pembacaan sensor pada tabel 'tb_data_sensor'.
// Data ini di-insert setiap kali Raspberry Pi Pico W mengirimkan telemetri berkala
// dan digunakan oleh aplikasi Android untuk menampilkan grafik tren kesehatan vital sign
// (Heart Rate BPM, Saturasi SpO2, dan dinamika percepatan gerak lansia).
public class DataSensorEnt implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long idData;            // Primary Key auto-increment tabel tb_data_sensor
    private Integer idPerangkat;    // Foreign Key ke tb_perangkat.id_perangkat
    private Double accelX;          // Percepatan sumbu X (g)
    private Double accelY;          // Percepatan sumbu Y (g)
    private Double accelZ;          // Percepatan sumbu Z (g)
    private Double gyroX;           // Kecepatan sudut X (deg/s)
    private Double gyroY;           // Kecepatan sudut Y (deg/s)
    private Double gyroZ;           // Kecepatan sudut Z (deg/s)
    private Double svm;             // Signal Vector Magnitude gerak tubuh
    private Double pitch;           // Derajat kemiringan pitch
    private Double roll;            // Derajat kemiringan roll
    private Integer heartRate;      // Denyut nadi BPM
    private Integer spo2;           // Saturasi oksigen darah %
    private Double latitude;        // Titik lintang GPS
    private Double longitude;       // Titik bujur GPS
    private String waktuBaca;       // Waktu timestamp pembacaan data

    public DataSensorEnt() {
    }

    public Long getIdData() {
        return idData;
    }

    public void setIdData(Long idData) {
        this.idData = idData;
    }

    public Integer getIdPerangkat() {
        return idPerangkat;
    }

    public void setIdPerangkat(Integer idPerangkat) {
        this.idPerangkat = idPerangkat;
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

    public String getWaktuBaca() {
        return waktuBaca;
    }

    public void setWaktuBaca(String waktuBaca) {
        this.waktuBaca = waktuBaca;
    }
}
