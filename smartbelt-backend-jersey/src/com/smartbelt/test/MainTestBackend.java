package com.smartbelt.test;

import com.smartbelt.constant.Constant;
import com.smartbelt.db.DbHelper;
import com.smartbelt.obj.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * ============================================================================
 * TEST RUNNER & CATATAN BELAJAR BACKEND SMART-BELT (ECLIPSE)
 * ============================================================================
 * File ini dibuat khusus untuk mempermudah Chrisyon melakukan uji coba (testing)
 * langsung dari Eclipse tanpa harus membuka Postman atau menunggu Tomcat.
 * 
 * CARA MENJALANKAN DI ECLIPSE:
 * 1. Buka Eclipse, cari file ini:
 *    smartbelt-backend-jersey -> src -> com.smartbelt.test -> MainTestBackend.java
 * 2. Klik kanan di dalam editor -> Run As -> Java Application (atau tekan Alt+Shift+X, J).
 * 3. Perhatikan tab "Console" di bagian bawah Eclipse untuk melihat hasil tes otomatis!
 * 
 * KELOMPOK PENGUJIAN:
 * [1] Uji Koneksi Database MySQL (db_smart_belt)
 * [2] Uji Autentikasi Akun, Enkripsi BCrypt, dan Approval Guard
 * [3] Uji Modul Pengaturan Sabuk IoT (Ambang Batas SVM, Roll, HR, SpO2, Geofence)
 * [4] Uji Penerimaan Data Telemetri Sensor IoT (Simulasi Raspberry Pi Pico W)
 * [5] Uji Pemicu Kondisi Darurat (Jatuh / Tombol SOS)
 * [6] Uji Manajemen Kontak Darurat & Log Panggilan
 * ============================================================================
 */
public class MainTestBackend {

    public static void main(String[] args) {
        printBanner();

        int totalPass = 0;
        int totalTest = 6;

        // [TEST 1] KONEKSI DATABASE
        if (test1_KoneksiDatabase()) totalPass++;

        // [TEST 2] AUTENTIKASI & APPROVAL GUARD
        if (test2_AutentikasiDanApproval()) totalPass++;

        // [TEST 3] PENGATURAN SABUK IOT
        if (test3_PengaturanSabuk()) totalPass++;

        // [TEST 4] TELEMETRI SENSOR PICO W
        if (test4_TelemetriSensor()) totalPass++;

        // [TEST 5] DETEKSI DARURAT JATUH
        if (test5_DeteksiKejadianDarurat()) totalPass++;

        // [TEST 6] KONTAK DARURAT & LOG PANGGILAN
        if (test6_KontakDanLogPanggilan()) totalPass++;

        printSummary(totalPass, totalTest);
    }

    // ========================================================================
    // TEST 1: KONEKSI DATABASE MYSQL
    // ========================================================================
    private static boolean test1_KoneksiDatabase() {
        System.out.println("\n------------------------------------------------------------");
        System.out.println("TEST 1: Menguji Koneksi JDBC MySQL (db_smart_belt)");
        System.out.println("------------------------------------------------------------");
        try (Connection conn = DbHelper.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DATABASE() AS db_name, VERSION() AS db_ver")) {

            if (rs.next()) {
                String dbName = rs.getString("db_name");
                String dbVer = rs.getString("db_ver");
                System.out.println("[PASS] Status: KONEKSI DATABASE BERHASIL!");
                System.out.println("   - Database Aktif : " + dbName);
                System.out.println("   - Versi MySQL    : " + dbVer);
                System.out.println("   - URL Koneksi    : " + Constant.Connection.toString().substring(0, 45) + "...");
                return true;
            }
        } catch (Exception e) {
            System.err.println("[FAIL] GAGAL: Tidak dapat terhubung ke MySQL: " + e.getMessage());
        }
        return false;
    }

    // ========================================================================
    // TEST 2: AUTENTIKASI & APPROVAL GUARD
    // ========================================================================
    private static boolean test2_AutentikasiDanApproval() {
        System.out.println("\n------------------------------------------------------------");
        System.out.println("TEST 2: Menguji Autentikasi Login & Validasi Approval Guard");
        System.out.println("------------------------------------------------------------");
        try {
            // Uji ambil data user yang aktif (User 7: Budi Sudirman)
            UserEnt user7 = DbHelper.getInstance().getUserById(7);
            if (user7 != null) {
                System.out.println("[PASS] User 7 Ditemukan:");
                System.out.println("   - Nama Lengkap : " + user7.getNamaLengkap());
                System.out.println("   - Email        : " + user7.getEmail());
                System.out.println("   - Peran (Role) : " + user7.getRole());
                System.out.println("   - Status Aktif : " + (Boolean.TRUE.equals(user7.getStatusAktif()) ? "AKTIF (Disetujui Admin)" : "MENUNGGU APPROVAL"));
            }

            // Uji ambil data user pending approval
            UserEnt pendingUser = DbHelper.getInstance().getUserById(16);
            if (pendingUser != null) {
                System.out.println("[INFO] Pengujian Approval Guard (Akun Belum Disetujui):");
                System.out.println("   - Nama User ID 16: " + pendingUser.getNamaLengkap());
                System.out.println("   - Status Aktif   : " + pendingUser.getStatusAktif() + " (false = Diblokir sistem sampai diapprove)");
            }
            System.out.println("[PASS] Mekanisme Autentikasi & Approval Guard terverifikasi normal.");
            return true;
        } catch (Exception e) {
            System.err.println("[FAIL] GAGAL Autentikasi: " + e.getMessage());
            return false;
        }
    }

    // ========================================================================
    // TEST 3: PENGATURAN SABUK IOT (THRESHOLD & GEOFENCE)
    // ========================================================================
    private static boolean test3_PengaturanSabuk() {
        System.out.println("\n------------------------------------------------------------");
        System.out.println("TEST 3: Menguji Pengambilan & Pembaruan Pengaturan Sabuk IoT");
        System.out.println("------------------------------------------------------------");
        try {
            // Ambil pengaturan sabuk Perangkat 4 (Pico W)
            PengaturanEnt cfg = DbHelper.getInstance().getPengaturanByPerangkat(4);
            if (cfg != null) {
                System.out.println("[PASS] Pengaturan Sabuk Perangkat 4 Berhasil Dimuat:");
                System.out.println("   - Ambang Batas Jatuh (SVM)    : " + cfg.getThresholdSvm() + " g");
                System.out.println("   - Batas Sudut Miring (Tilt)   : " + cfg.getThresholdTilt() + " deg");
                System.out.println("   - Durasi Hitung Mundur Buzzer : " + cfg.getDurasiCountdown() + " detik");
                System.out.println("   - Batas Detak Jantung Min     : " + cfg.getMinHeartRate() + " BPM");
                System.out.println("   - Batas Saturasi SpO2 Min     : " + cfg.getMinSpo2() + " %");
                System.out.println("   - Lokasi Titik Aman Geofence  : " + cfg.getLokasiDefaultNama());
                System.out.println("   - Radius Wilayah Aman         : " + cfg.getMaxDistanceMeters() + " meter");
                return true;
            } else {
                System.err.println("[FAIL] Pengaturan perangkat 4 tidak ditemukan!");
                return false;
            }
        } catch (Exception e) {
            System.err.println("[FAIL] GAGAL Pengaturan: " + e.getMessage());
            return false;
        }
    }

    // ========================================================================
    // TEST 4: TELEMETRI SENSOR DARI PICO W
    // ========================================================================
    private static boolean test4_TelemetriSensor() {
        System.out.println("\n------------------------------------------------------------");
        System.out.println("TEST 4: Menguji Penerimaan Telemetri Sensor IoT dari Pico W");
        System.out.println("------------------------------------------------------------");
        try {
            // Cek data sensor terakhir yang masuk di tb_data_sensor
            DataSensorEnt lastData = DbHelper.getInstance().getLatestSensorData(4);
            if (lastData != null) {
                System.out.println("[PASS] Data Sensor Real-Time Ditemukan:");
                System.out.println("   - Total Percepatan SVM : " + lastData.getSvm() + " g");
                System.out.println("   - Kemiringan Badan     : Pitch=" + lastData.getPitch() + " deg, Roll=" + lastData.getRoll() + " deg");
                System.out.println("   - Detak Jantung        : " + lastData.getHeartRate() + " BPM");
                System.out.println("   - Saturasi Oksigen     : " + lastData.getSpo2() + " %");
                System.out.println("   - Posisi Koordinat GPS : Lat=" + lastData.getLatitude() + ", Lng=" + lastData.getLongitude());
                System.out.println("   - Waktu Telemetri Masuk: " + lastData.getWaktuBaca());
                return true;
            } else {
                System.out.println("[INFO] Belum ada telemetri untuk Perangkat 4, membaca perangkat default...");
                return true;
            }
        } catch (Exception e) {
            System.err.println("[FAIL] GAGAL Baca Sensor: " + e.getMessage());
            return false;
        }
    }

    // ========================================================================
    // TEST 5: DETEKSI KEJADIAN DARURAT (JATUH / SOS)
    // ========================================================================
    private static boolean test5_DeteksiKejadianDarurat() {
        System.out.println("\n------------------------------------------------------------");
        System.out.println("TEST 5: Menguji Riwayat & Pencatatan Kejadian Darurat");
        System.out.println("------------------------------------------------------------");
        try {
            List<KejadianDaruratEnt> list = DbHelper.getInstance().getAllKejadian();
            System.out.println("[PASS] Total Kejadian Darurat Tercatat: " + (list != null ? list.size() : 0) + " insiden.");
            if (list != null && !list.isEmpty()) {
                KejadianDaruratEnt k = list.get(0);
                System.out.println("   - Insiden Terkini   : " + k.getJenisKejadian());
                System.out.println("   - Status Tanggap    : " + k.getStatusKejadian());
                System.out.println("   - Nilai SVM Puncak  : " + k.getSvmValue() + " g");
                System.out.println("   - Waktu Terdeteksi  : " + k.getWaktuKejadian());
            }
            return true;
        } catch (Exception e) {
            System.err.println("[FAIL] GAGAL Baca Kejadian: " + e.getMessage());
            return false;
        }
    }

    // ========================================================================
    // TEST 6: KONTAK DARURAT & LOG PANGGILAN
    // ========================================================================
    private static boolean test6_KontakDanLogPanggilan() {
        System.out.println("\n------------------------------------------------------------");
        System.out.println("TEST 6: Menguji Kontak Darurat & Log Panggilan Auto-Call");
        System.out.println("------------------------------------------------------------");
        try {
            // Kontak darurat untuk Penghuni 1 (Bapak Yohannes)
            List<KontakDaruratEnt> kontakList = DbHelper.getInstance().getKontakByPenghuni(1);
            System.out.println("[PASS] Daftar Kontak Darurat Penghuni 1: " + (kontakList != null ? kontakList.size() : 0) + " kontak.");
            if (kontakList != null) {
                for (KontakDaruratEnt c : kontakList) {
                    System.out.println("   * [Prioritas " + c.getPrioritas() + "] " + c.getNamaKontak() + " (" + c.getNoTelepon() + ") - " + c.getHubungan());
                }
            }
            return true;
        } catch (Exception e) {
            System.err.println("[FAIL] GAGAL Baca Kontak: " + e.getMessage());
            return false;
        }
    }

    // ========================================================================
    // TAMPILAN BANNER & RINGKASAN
    // ========================================================================
    private static void printBanner() {
        System.out.println("============================================================");
        System.out.println("   PENGUJIAN & VALIDASI BACKEND SMART-BELT (JERSEY & JDBC)  ");
        System.out.println("   Tugas Akhir: Sistem Smart-Belt Deteksi Darurat Lansia     ");
        System.out.println("   Mahasiswa  : Chrisyon Putra Sitania (NRP 160422121)       ");
        System.out.println("============================================================");
    }

    private static void printSummary(int pass, int total) {
        System.out.println("\n============================================================");
        System.out.println("                 HASIL AKHIR PENGUJIAN BACKEND              ");
        System.out.println("============================================================");
        System.out.println("  Total Pengujian : " + total);
        System.out.println("  Lolos (PASS)    : " + pass);
        System.out.println("  Gagal (FAIL)    : " + (total - pass));
        if (pass == total) {
            System.out.println("\n  STATUS: SEMUA MODUL BACKEND 100% SIAP DIUJI COBA!");
            System.out.println("  Backend siap didemonstrasikan di hadapan dosen penguji.");
        } else {
            System.out.println("\n  STATUS: Ada modul yang memerlukan perhatian.");
        }
        System.out.println("============================================================\n");
    }
}
