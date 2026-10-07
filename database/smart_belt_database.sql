CREATE DATABASE IF NOT EXISTS db_smart_belt
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE db_smart_belt;

-- ==============================================================================
-- TABEL 1: TB_USER (Akun Pengguna - Login, Register, Auth)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS tb_user (
    id_user         INT             AUTO_INCREMENT PRIMARY KEY,
    nama_lengkap    VARCHAR(100)    NOT NULL,
    email           VARCHAR(100)    NOT NULL UNIQUE COMMENT 'Email untuk login & reset password',
    username        VARCHAR(50)     NOT NULL UNIQUE COMMENT 'Username untuk login',
    password_hash   VARCHAR(255)    NOT NULL        COMMENT 'Password terenkripsi (BCrypt)',
    role            ENUM('ADMIN','CAREGIVER','KELUARGA')
                                    NOT NULL DEFAULT 'KELUARGA'
                                    COMMENT 'Hak akses pengguna',
    no_telepon      VARCHAR(20)     NULL,
    telegram_chat_id VARCHAR(50)    NULL            COMMENT 'Chat ID Telegram untuk notifikasi darurat',
    foto_profil     VARCHAR(255)    NULL            COMMENT 'Path/URL foto profil',
    email_verified  TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '1=Email terverifikasi, 0=Belum',
    status_aktif    TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '1=Aktif, 0=Diblokir',
    last_login      DATETIME        NULL            COMMENT 'Waktu login terakhir',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NULL ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_user_email (email),
    INDEX idx_user_username (username),
    INDEX idx_user_role (role)
) ENGINE=InnoDB;

-- ==============================================================================
-- TABEL 2: TB_RESET_PASSWORD (Token Reset Password via Email)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS tb_reset_password (
    id_reset        INT             AUTO_INCREMENT PRIMARY KEY,
    id_user         INT             NOT NULL,
    token           VARCHAR(255)    NOT NULL UNIQUE COMMENT 'Token acak dikirim ke email',
    expired_at      DATETIME        NOT NULL        COMMENT 'Waktu kedaluwarsa token (misal 30 menit)',
    is_used         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '1=Sudah dipakai, 0=Belum',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_reset_user
        FOREIGN KEY (id_user) REFERENCES tb_user(id_user)
        ON DELETE CASCADE ON UPDATE CASCADE,

    INDEX idx_reset_token (token),
    INDEX idx_reset_user (id_user)
) ENGINE=InnoDB;

-- ==============================================================================
-- TABEL 3: TB_SESSION (Sesi Login Aktif / Token JWT)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS tb_session (
    id_session      INT             AUTO_INCREMENT PRIMARY KEY,
    id_user         INT             NOT NULL,
    token_session   VARCHAR(500)    NOT NULL UNIQUE COMMENT 'JWT Token atau Session Token',
    device_info     VARCHAR(255)    NULL            COMMENT 'Info perangkat (Android model, dll)',
    ip_address      VARCHAR(45)     NULL            COMMENT 'IP Address saat login',
    expired_at      DATETIME        NOT NULL        COMMENT 'Waktu kedaluwarsa sesi',
    is_active       TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '1=Aktif, 0=Logout/Expired',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_session_user
        FOREIGN KEY (id_user) REFERENCES tb_user(id_user)
        ON DELETE CASCADE ON UPDATE CASCADE,

    INDEX idx_session_token (token_session),
    INDEX idx_session_user (id_user)
) ENGINE=InnoDB;

-- ==============================================================================
-- TABEL 4: TB_PENGHUNI (Data Lansia Wisma Panti INAKAKA Ambon)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS tb_penghuni (
    id_penghuni     INT             AUTO_INCREMENT PRIMARY KEY,
    id_user         INT             NULL            COMMENT 'Link ke akun user keluarga/caregiver penanggung jawab',
    id_user_lansia  INT             NULL            COMMENT 'Link ke akun user lansia/pengguna belt itu sendiri',
    nama_lengkap    VARCHAR(100)    NOT NULL,
    tanggal_lahir   DATE            NULL,
    jenis_kelamin   ENUM('L','P')   NULL            COMMENT 'L=Laki-laki, P=Perempuan',
    alamat_kamar    VARCHAR(50)     NULL            COMMENT 'Nomor/nama kamar di Wisma Panti',
    no_telepon      VARCHAR(20)     NULL,
    riwayat_penyakit TEXT           NULL            COMMENT 'Catatan riwayat penyakit (stroke, jantung, dll)',
    foto_profil     VARCHAR(255)    NULL            COMMENT 'Path/URL file foto profil',
    status_aktif    TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '1=Aktif, 0=Nonaktif',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NULL ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_penghuni_user
        FOREIGN KEY (id_user) REFERENCES tb_user(id_user)
        ON DELETE SET NULL ON UPDATE CASCADE,

    CONSTRAINT fk_penghuni_user_lansia
        FOREIGN KEY (id_user_lansia) REFERENCES tb_user(id_user)
        ON DELETE SET NULL ON UPDATE CASCADE,

    INDEX idx_penghuni_user (id_user),
    INDEX idx_penghuni_user_lansia (id_user_lansia)
) ENGINE=InnoDB;

-- ==============================================================================
-- TABEL 5: TB_KONTAK_DARURAT (Kontak Darurat / Caregiver)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS tb_kontak_darurat (
    id_kontak       INT             AUTO_INCREMENT PRIMARY KEY,
    id_penghuni     INT             NOT NULL,
    nama_kontak     VARCHAR(100)    NOT NULL,
    no_telepon      VARCHAR(20)     NOT NULL,
    hubungan        ENUM('KELUARGA','CAREGIVER','DOKTER','RS','LAINNYA')
                                    NOT NULL DEFAULT 'KELUARGA'
                                    COMMENT 'Hubungan dengan penghuni',
    prioritas       INT             NOT NULL DEFAULT 1 COMMENT '1=Utama, 2=Sekunder, 3=Tersier',
    status_aktif    TINYINT(1)      NOT NULL DEFAULT 1,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NULL ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_kontak_penghuni
        FOREIGN KEY (id_penghuni) REFERENCES tb_penghuni(id_penghuni)
        ON DELETE CASCADE ON UPDATE CASCADE,

    INDEX idx_kontak_penghuni (id_penghuni),
    INDEX idx_kontak_prioritas (prioritas)
) ENGINE=InnoDB;

-- ==============================================================================
-- TABEL 6: TB_PERANGKAT (Data Perangkat Smart-Belt / Pico W)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS tb_perangkat (
    id_perangkat        INT             AUTO_INCREMENT PRIMARY KEY,
    id_penghuni         INT             NULL        COMMENT '1 perangkat = 1 penghuni (NULL jika belum dipasangkan)',
    nama_perangkat      VARCHAR(50)     NOT NULL    COMMENT 'Label perangkat, misal: Smart-Belt #01',
    mac_address         VARCHAR(17)     NULL UNIQUE COMMENT 'MAC Address Wi-Fi Pico W',
    ip_address          VARCHAR(45)     NULL        COMMENT 'IP Address saat terhubung',
    versi_firmware      VARCHAR(20)     NULL        COMMENT 'Versi firmware MicroPython',
    status_baterai      INT             NULL        COMMENT 'Persentase baterai 0-100',
    status_koneksi      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '1=Online, 0=Offline',
    terakhir_terhubung  DATETIME        NULL        COMMENT 'Waktu terakhir perangkat online',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NULL ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_perangkat_penghuni
        FOREIGN KEY (id_penghuni) REFERENCES tb_penghuni(id_penghuni)
        ON DELETE SET NULL ON UPDATE CASCADE,

    INDEX idx_perangkat_penghuni (id_penghuni)
) ENGINE=InnoDB;

-- ==============================================================================
-- TABEL 7: TB_KEJADIAN_DARURAT (Log Insiden / Deteksi Jatuh)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS tb_kejadian_darurat (
    id_kejadian     INT             AUTO_INCREMENT PRIMARY KEY,
    id_penghuni     INT             NOT NULL,
    id_perangkat    INT             NOT NULL,
    jenis_kejadian  ENUM('JATUH','PANIC_BUTTON','TILT_EKSTREM','POSISI_MIRING_LAMA','HEART_RATE_RENDAH','SPO2_RENDAH','KELUAR_ZONA_AMAN')
                                    NOT NULL        COMMENT 'Jenis kejadian darurat',
    svm_value       DECIMAL(6,3)    NULL            COMMENT 'Signal Vector Magnitude (g) saat kejadian',
    pitch_value     DECIMAL(6,2)    NULL            COMMENT 'Sudut Pitch (derajat) saat kejadian',
    roll_value      DECIMAL(6,2)    NULL            COMMENT 'Sudut Roll (derajat) saat kejadian',
    accel_x         DECIMAL(6,3)    NULL            COMMENT 'Akselerometer X (g)',
    accel_y         DECIMAL(6,3)    NULL            COMMENT 'Akselerometer Y (g)',
    accel_z         DECIMAL(6,3)    NULL            COMMENT 'Akselerometer Z (g)',
    latitude        DECIMAL(10,7)   NULL            COMMENT 'Koordinat GPS Latitude',
    longitude       DECIMAL(10,7)   NULL            COMMENT 'Koordinat GPS Longitude',
    alamat_lokasi   TEXT            NULL            COMMENT 'Alamat hasil Reverse Geocoding',
    status_kejadian ENUM('TERDETEKSI','COUNTDOWN','AUTO_CALL','DIBATALKAN','SELESAI')
                                    NOT NULL DEFAULT 'TERDETEKSI'
                                    COMMENT 'Status alur kejadian',
    waktu_kejadian  DATETIME        NOT NULL        COMMENT 'Waktu kejadian terdeteksi',
    waktu_respon    DATETIME        NULL            COMMENT 'Waktu caregiver merespon',
    catatan         TEXT            NULL            COMMENT 'Catatan tambahan',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_kejadian_penghuni
        FOREIGN KEY (id_penghuni) REFERENCES tb_penghuni(id_penghuni)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_kejadian_perangkat
        FOREIGN KEY (id_perangkat) REFERENCES tb_perangkat(id_perangkat)
        ON DELETE CASCADE ON UPDATE CASCADE,

    INDEX idx_kejadian_penghuni (id_penghuni),
    INDEX idx_kejadian_perangkat (id_perangkat),
    INDEX idx_kejadian_waktu (waktu_kejadian),
    INDEX idx_kejadian_status (status_kejadian)
) ENGINE=InnoDB;

-- ==============================================================================
-- TABEL 8: TB_DATA_SENSOR (Telemetri Real-Time MPU6050, MAX30102 & GPS)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS tb_data_sensor (
    id_data         BIGINT          AUTO_INCREMENT PRIMARY KEY,
    id_perangkat    INT             NOT NULL,
    accel_x         DECIMAL(6,3)    NULL            COMMENT 'Akselerometer X (g)',
    accel_y         DECIMAL(6,3)    NULL            COMMENT 'Akselerometer Y (g)',
    accel_z         DECIMAL(6,3)    NULL            COMMENT 'Akselerometer Z (g)',
    gyro_x          DECIMAL(8,3)    NULL            COMMENT 'Giroskop X (deg/s)',
    gyro_y          DECIMAL(8,3)    NULL            COMMENT 'Giroskop Y (deg/s)',
    gyro_z          DECIMAL(8,3)    NULL            COMMENT 'Giroskop Z (deg/s)',
    svm             DECIMAL(6,3)    NULL            COMMENT 'Signal Vector Magnitude (g)',
    pitch           DECIMAL(6,2)    NULL            COMMENT 'Sudut Pitch (derajat)',
    roll            DECIMAL(6,2)    NULL            COMMENT 'Sudut Roll (derajat)',
    heart_rate      INT             NULL            COMMENT 'Detak jantung dari MAX30102 (BPM)',
    spo2            INT             NULL            COMMENT 'Saturasi oksigen dari MAX30102 (%)',
    latitude        DECIMAL(10,7)   NULL            COMMENT 'GPS Latitude',
    longitude       DECIMAL(10,7)   NULL            COMMENT 'GPS Longitude',
    waktu_baca      DATETIME        NOT NULL        COMMENT 'Waktu pembacaan sensor',

    CONSTRAINT fk_sensor_perangkat
        FOREIGN KEY (id_perangkat) REFERENCES tb_perangkat(id_perangkat)
        ON DELETE CASCADE ON UPDATE CASCADE,

    INDEX idx_sensor_perangkat (id_perangkat),
    INDEX idx_sensor_waktu (waktu_baca)
) ENGINE=InnoDB;

-- ==============================================================================
-- TABEL 9: TB_LOG_PANGGILAN (Log Panggilan Telepon & SMS Darurat)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS tb_log_panggilan (
    id_panggilan        INT             AUTO_INCREMENT PRIMARY KEY,
    id_kejadian         INT             NOT NULL,
    id_kontak           INT             NOT NULL,
    jenis_komunikasi    ENUM('TELEPON','SMS')
                                        NOT NULL    COMMENT 'Jenis komunikasi darurat',
    status_panggilan    ENUM('BERHASIL','GAGAL','TIDAK_DIJAWAB')
                                        NOT NULL    COMMENT 'Status hasil panggilan/SMS',
    isi_pesan_sms       TEXT            NULL        COMMENT 'Isi pesan SMS (berisi link Google Maps)',
    waktu_panggilan     DATETIME        NOT NULL    COMMENT 'Waktu panggilan dilakukan',
    durasi_detik        INT             NULL        COMMENT 'Durasi panggilan (detik)',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_panggilan_kejadian
        FOREIGN KEY (id_kejadian) REFERENCES tb_kejadian_darurat(id_kejadian)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_panggilan_kontak
        FOREIGN KEY (id_kontak) REFERENCES tb_kontak_darurat(id_kontak)
        ON DELETE CASCADE ON UPDATE CASCADE,

    INDEX idx_panggilan_kejadian (id_kejadian),
    INDEX idx_panggilan_kontak (id_kontak),
    INDEX idx_panggilan_waktu (waktu_panggilan)
) ENGINE=InnoDB;

-- ==============================================================================
-- TABEL 10: TB_PENGATURAN (Konfigurasi & Threshold Perangkat)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS tb_pengaturan (
    id_pengaturan       INT             AUTO_INCREMENT PRIMARY KEY,
    id_perangkat        INT             NOT NULL UNIQUE COMMENT '1 perangkat = 1 konfigurasi',
    threshold_svm       DECIMAL(4,2)    NOT NULL DEFAULT 2.20 COMMENT 'Ambang batas SVM deteksi jatuh (g)',
    threshold_tilt      DECIMAL(5,2)    NOT NULL DEFAULT 55.00 COMMENT 'Ambang batas sudut kemiringan (derajat)',
    durasi_miring_detik INT             NOT NULL DEFAULT 5     COMMENT 'Durasi miring terus-menerus sebelum alarm terpicu (detik)',
    threshold_roll_count INT            NOT NULL DEFAULT 5 COMMENT 'Jumlah miring berulang sebelum alarm (kali)',
    durasi_countdown    INT             NOT NULL DEFAULT 10 COMMENT 'Durasi countdown Auto-Call (detik)',
    interval_pembacaan  DECIMAL(4,2)    NOT NULL DEFAULT 0.20 COMMENT 'Interval pembacaan sensor (detik)',
    ssid_wifi           VARCHAR(50)     NULL        COMMENT 'Nama jaringan Wi-Fi',
    password_wifi       VARCHAR(100)    NULL        COMMENT 'Password Wi-Fi (terenkripsi)',
    buzzer_aktif        TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '1=Buzzer aktif, 0=Nonaktif',
    min_heart_rate      INT             NOT NULL DEFAULT 60 COMMENT 'Batas bawah detak jantung normal (BPM)',
    min_spo2            INT             NOT NULL DEFAULT 95 COMMENT 'Batas bawah saturasi oksigen normal (%)',
    lokasi_default_nama VARCHAR(100)    NOT NULL DEFAULT 'Wisma Panti INAKAKA Ambon' COMMENT 'Nama lokasi default',
    default_latitude    DECIMAL(10,7)   NOT NULL DEFAULT -3.6954360 COMMENT 'Latitude lokasi default',
    default_longitude   DECIMAL(10,7)   NOT NULL DEFAULT 128.1814270 COMMENT 'Longitude lokasi default',
    max_distance_meters INT             NOT NULL DEFAULT 100 COMMENT 'Batas radius jarak maksimal dari lokasi default (meter)',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NULL ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_pengaturan_perangkat
        FOREIGN KEY (id_perangkat) REFERENCES tb_perangkat(id_perangkat)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;
-- ==============================================================================
-- VERIFIKASI: Cek semua tabel berhasil dibuat
-- ==============================================================================
-- TABEL 11: TB_KELUARGA_PENGHUNI (Relasi Many-to-Many: Banyak Keluarga per Penghuni)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS tb_keluarga_penghuni (
    id_keluarga_penghuni INT         AUTO_INCREMENT PRIMARY KEY,
    id_penghuni          INT         NOT NULL        COMMENT 'FK ke tb_penghuni (lansia)',
    id_user              INT         NOT NULL        COMMENT 'FK ke tb_user (akun keluarga)',
    hubungan             VARCHAR(50) NULL            COMMENT 'Hubungan: Anak, Cucu, Menantu, dll',
    created_at           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_kp_penghuni
        FOREIGN KEY (id_penghuni) REFERENCES tb_penghuni(id_penghuni)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT fk_kp_user
        FOREIGN KEY (id_user) REFERENCES tb_user(id_user)
        ON DELETE CASCADE ON UPDATE CASCADE,

    UNIQUE KEY uk_penghuni_user (id_penghuni, id_user),
    INDEX idx_kp_penghuni (id_penghuni),
    INDEX idx_kp_user (id_user)
) ENGINE=InnoDB;

-- ==============================================================================
SELECT 'DATABASE SMART-BELT BERHASIL DIBUAT!' AS status;
SHOW TABLES;

SELECT '--- RINGKASAN TABEL ---' AS info;
SELECT 'tb_user'              AS tabel, COUNT(*) AS jumlah_data FROM tb_user
UNION ALL
SELECT 'tb_reset_password',            COUNT(*) FROM tb_reset_password
UNION ALL
SELECT 'tb_session',                   COUNT(*) FROM tb_session
UNION ALL
SELECT 'tb_penghuni',                  COUNT(*) FROM tb_penghuni
UNION ALL
SELECT 'tb_keluarga_penghuni',         COUNT(*) FROM tb_keluarga_penghuni
UNION ALL
SELECT 'tb_kontak_darurat',            COUNT(*) FROM tb_kontak_darurat
UNION ALL
SELECT 'tb_perangkat',                 COUNT(*) FROM tb_perangkat
UNION ALL
SELECT 'tb_kejadian_darurat',          COUNT(*) FROM tb_kejadian_darurat
UNION ALL
SELECT 'tb_data_sensor',              COUNT(*) FROM tb_data_sensor
UNION ALL
SELECT 'tb_log_panggilan',            COUNT(*) FROM tb_log_panggilan
UNION ALL
SELECT 'tb_pengaturan',               COUNT(*) FROM tb_pengaturan;
