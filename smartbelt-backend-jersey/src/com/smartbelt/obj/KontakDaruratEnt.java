package com.smartbelt.obj;

import java.io.Serializable;

// ============================================================================
// MODEL ENTITY KONTAK DARURAT (KontakDaruratEnt)
// ============================================================================
// Merepresentasikan daftar kontak telepon darurat keluarga pada tabel 'tb_kontak_darurat'.
// Nomor-nomor telepon ini dijadikan tujuan panggilan suara dan SMS darurat oleh
// fitur Auto-Call Emergency aplikasi Android saat terjadi insiden jatuh pada lansia.
public class KontakDaruratEnt implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer idKontak;       // Primary Key tabel tb_kontak_darurat
    private Integer idPenghuni;     // Foreign Key ke tb_penghuni.id_penghuni
    private String namaKontak;      // Nama anggota keluarga / penanggung jawab
    private String noTelepon;       // Nomor telepon seluler / WhatsApp (+62...)
    private String hubungan;        // Hubungan keluarga (ANAK, SUAMI, ISTRI, SAUDARA, dll.)
    private Integer prioritas;      // Urutan prioritas panggilan (1 = Utama, 2 = Cadangan, dst.)
    private Boolean statusAktif;    // Status aktif kontak (true = Aktif)

    public KontakDaruratEnt() {
    }

    public Integer getIdKontak() {
        return idKontak;
    }

    public void setIdKontak(Integer idKontak) {
        this.idKontak = idKontak;
    }

    public Integer getIdPenghuni() {
        return idPenghuni;
    }

    public void setIdPenghuni(Integer idPenghuni) {
        this.idPenghuni = idPenghuni;
    }

    public String getNamaKontak() {
        return namaKontak;
    }

    public void setNamaKontak(String namaKontak) {
        this.namaKontak = namaKontak;
    }

    public String getNoTelepon() {
        return noTelepon;
    }

    public void setNoTelepon(String noTelepon) {
        this.noTelepon = noTelepon;
    }

    public String getHubungan() {
        return hubungan;
    }

    public void setHubungan(String hubungan) {
        this.hubungan = hubungan;
    }

    public Integer getPrioritas() {
        return prioritas;
    }

    public void setPrioritas(Integer prioritas) {
        this.prioritas = prioritas;
    }

    public Boolean getStatusAktif() {
        return statusAktif;
    }

    public void setStatusAktif(Boolean statusAktif) {
        this.statusAktif = statusAktif;
    }
}
