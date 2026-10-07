package com.smartbelt.obj;

import java.io.Serializable;

// ============================================================================
// MODEL ENTITY PERANGKAT SABUK IOT (PerangkatEnt)
// ============================================================================
// Merepresentasikan data inventaris fisik sabuk pintar pada tabel 'tb_perangkat'.
// Menyimpan nomor MAC address WiFi/Bluetooth Pico W, IP Address di jaringan lokal,
// versi firmware MicroPython, status baterai (%), status koneksi, dan detak komunikasi terakhir.
public class PerangkatEnt implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer idPerangkat;        // Primary Key tabel tb_perangkat
    private String namaPerangkat;       // Nama label sabuk ("Smart-Belt 01")
    private String macAddress;          // Alamat fisik hardware MAC Pico W
    private Integer idPenghuni;         // ID lansia pemakai (tb_penghuni.id_penghuni)
    private String ipAddress;           // IP Address yang didapat dari router WiFi (DHCP)
    private String versiFirmware;       // Versi skrip program MicroPython ("v1.0.0")
    private Integer statusBaterai;      // Persentase baterai sabuk (%)
    private Boolean statusKoneksi;      // Status koneksi (true = Online, false = Offline)
    private String terakhirTerhubung;   // Waktu timestamp terakhir sabuk berkirim data

    public PerangkatEnt() {
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

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getVersiFirmware() {
        return versiFirmware;
    }

    public void setVersiFirmware(String versiFirmware) {
        this.versiFirmware = versiFirmware;
    }

    public Integer getStatusBaterai() {
        return statusBaterai;
    }

    public void setStatusBaterai(Integer statusBaterai) {
        this.statusBaterai = statusBaterai;
    }

    public Boolean getStatusKoneksi() {
        return statusKoneksi;
    }

    public void setStatusKoneksi(Boolean statusKoneksi) {
        this.statusKoneksi = statusKoneksi;
    }

    public String getTerakhirTerhubung() {
        return terakhirTerhubung;
    }

    public void setTerakhirTerhubung(String terakhirTerhubung) {
        this.terakhirTerhubung = terakhirTerhubung;
    }
}
