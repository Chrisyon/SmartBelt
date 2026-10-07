package com.smartbelt.db;

import com.smartbelt.constant.Constant;
import com.smartbelt.obj.*;
import com.smartbelt.util.EmailHelper;
import com.smartbelt.util.TelegramHelper;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.logging.Logger;

public class DbHelper {
    private static final Logger LOG = Logger.getLogger(DbHelper.class.getName());
    private static DbHelper instance = null;

    public static DbHelper getInstance() {
        if (instance == null) {
            instance = new DbHelper();
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                instance.autoMigrateSchema();
            } catch (Exception e) {
                LOG.severe("Database driver/migration notice: " + e.getMessage());
            }
        }
        return instance;
    }

    private void autoMigrateSchema() {
        Connection conn = null;
        Statement st = null;
        try {
            conn = getConnection();
            st = conn.createStatement();
            // Migrasi kolom baru tb_pengaturan
            try { st.execute("ALTER TABLE tb_pengaturan ADD COLUMN durasi_miring_detik INT NOT NULL DEFAULT 5 COMMENT 'Durasi miring terus menerus sebelum alarm (detik)'"); } catch (Exception ignored) {}
            try { st.execute("ALTER TABLE tb_pengaturan ADD COLUMN min_heart_rate INT NOT NULL DEFAULT 60 COMMENT 'Batas bawah detak jantung normal (BPM)'"); } catch (Exception ignored) {}
            try { st.execute("ALTER TABLE tb_pengaturan ADD COLUMN min_spo2 INT NOT NULL DEFAULT 95 COMMENT 'Batas bawah saturasi oksigen normal (%)'"); } catch (Exception ignored) {}
            try { st.execute("ALTER TABLE tb_pengaturan ADD COLUMN lokasi_default_nama VARCHAR(100) NOT NULL DEFAULT 'Wisma Panti Lansia'"); } catch (Exception ignored) {}
            try { st.execute("ALTER TABLE tb_pengaturan ADD COLUMN default_latitude DECIMAL(10,7) NOT NULL DEFAULT -3.6954360"); } catch (Exception ignored) {}
            try { st.execute("ALTER TABLE tb_pengaturan ADD COLUMN default_longitude DECIMAL(10,7) NOT NULL DEFAULT 128.1814270"); } catch (Exception ignored) {}
            try { st.execute("ALTER TABLE tb_pengaturan ADD COLUMN max_distance_meters INT NOT NULL DEFAULT 100"); } catch (Exception ignored) {}
            // Migrasi perluasan enum jenis_kejadian di tb_kejadian_darurat
            try { st.execute("ALTER TABLE tb_kejadian_darurat MODIFY COLUMN jenis_kejadian ENUM('JATUH','PANIC_BUTTON','TILT_EKSTREM','HEART_RATE_RENDAH','SPO2_RENDAH','KELUAR_ZONA_AMAN','POSISI_MIRING_LAMA') NOT NULL"); } catch (Exception ignored) {}
        } catch (Exception e) {
            LOG.info("Auto migrate schema info: " + e.getMessage());
        } finally {
            closeResource(st);
            closeResource(conn);
        }
    }

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("com.mysql.jdbc.Driver");
            } catch (Exception ignored) {}
        }
    }

    public Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("com.mysql.jdbc.Driver");
            } catch (Exception ignored) {}
        }
        return DriverManager.getConnection(
                Constant.Connection.toString(),
                Constant.User.toString(),
                Constant.Password.toString()
        );
    }

    // Utilitas penutup koneksi, statement, dan result set secara aman untuk mencegah memory leak
    public static void closeResource(AutoCloseable res) {
        if (res != null) {
            try {
                res.close();
            } catch (Exception ignored) {
            }
        }
    }

    // ========================================================================
    // 1. MODUL OTENTIKASI & PENGGUNA (tb_user & tb_session)
    // ========================================================================

    // ------------------------------------------------------------------------
    // METHOD: login(usernameOrEmail, password)
    // ------------------------------------------------------------------------
    // Fungsi:
    // 1. Mencari user di 'tb_user' berdasarkan username atau email (case-insensitive).
    // 2. Mengecek hash password dengan BCrypt (support fallback plain text jika ada data lama).
    // 3. Memeriksa status_aktif: jika bernilai 0, lemparkan SecurityException karena akun
    //    yang baru mendaftar mandiri wajib di-ACC (disetujui) oleh Admin Wisma terlebih dahulu.
    // 4. Jika lolos:
    //    - Update timestamp 'last_login' menjadi waktu saat ini (NOW()).
    //    - Buat session token Bearer baru berawalan 'JWT_' dan simpan ke 'tb_session' (expired 30 hari).
    //    - Kembalikan objek AuthResponseEnt berisi token, ID user, nama, role, dll.
    public AuthResponseEnt login(String usernameOrEmail, String password) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            String sql = "SELECT * FROM tb_user WHERE (LOWER(username) = LOWER(?) OR LOWER(email) = LOWER(?)) LIMIT 1";
            ps = conn.prepareStatement(sql);
            ps.setString(1, usernameOrEmail);
            ps.setString(2, usernameOrEmail);
            rs = ps.executeQuery();

            if (rs.next()) {
                String hash = rs.getString("password_hash");
                boolean passValid = false;

                // Verifikasi password hash menggunakan algoritma BCrypt
                if (hash != null && hash.startsWith("$2")) {
                    try {
                        passValid = BCrypt.checkpw(password, hash);
                    } catch (Exception ignored) {
                    }
                }
                // Fallback untuk password lama jika belum terenkripsi BCrypt
                if (!passValid && hash != null) {
                    passValid = hash.equals(password);
                }

                if (passValid) {
                    // Validasi status persetujuan Admin (Approval Guard)
                    int statusAktif = rs.getInt("status_aktif");
                    if (statusAktif == 0) {
                        throw new SecurityException("AKUN_MENUNGGU_APPROVAL: Akun Anda sedang menunggu persetujuan (approval) oleh Admin Wisma. Silakan hubungi Admin untuk mengaktifkan akun Anda.");
                    }

                    int idUser = rs.getInt("id_user");
                    String username = rs.getString("username");
                    String email = rs.getString("email");
                    String nama = rs.getString("nama_lengkap");
                    String role = rs.getString("role");
                    String foto = rs.getString("foto_profil");

                    // Generate token session acak berbasis UUID
                    String token = "JWT_" + UUID.randomUUID().toString().replace("-", "");

                    // Catat waktu login terakhir
                    PreparedStatement psUpdate = conn.prepareStatement("UPDATE tb_user SET last_login = NOW() WHERE id_user = ?");
                    psUpdate.setInt(1, idUser);
                    psUpdate.executeUpdate();
                    closeResource(psUpdate);

                    // Buat sesi aktif di tb_session dengan masa berlaku 30 hari
                    PreparedStatement psSess = conn.prepareStatement(
                            "INSERT INTO tb_session (id_user, token_session, device_info, expired_at, is_active) " +
                                    "VALUES (?, ?, 'Android Caregiver App', DATE_ADD(NOW(), INTERVAL 30 DAY), 1)"
                    );
                    psSess.setInt(1, idUser);
                    psSess.setString(2, token);
                    psSess.executeUpdate();
                    closeResource(psSess);

                    return new AuthResponseEnt(token, "Bearer", idUser, username, email, nama, role, foto);
                }
            }
            return null;
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: register(user)
    // ------------------------------------------------------------------------
    // Fungsi:
    // 1. Validasi keunikan username dan email di tabel 'tb_user'. Jika sudah ada, return null.
    // 2. Normalisasi role (ADMIN, CAREGIVER, KELUARGA, PENGGUNA_BELT / LANSIA). Default: KELUARGA.
    // 3. Menghitung password hash menggunakan BCrypt.gensalt(10) demi keamanan data.
    // 4. Menyimpan record baru ke 'tb_user' dengan status_aktif = 0 (menunggu approval Admin).
    // 5. Otomasi Relasi Data:
    //    a. Jika mendaftar sebagai PENGGUNA_BELT / LANSIA:
    //       Otomatis meng-insert record baru ke 'tb_penghuni' (id_user_lansia = id_user baru).
    //    b. Jika mendaftar sebagai KELUARGA dan memilih lansia yang dirawat:
    //       Otomatis meng-insert record ke 'tb_keluarga_penghuni' (id_penghuni + id_user + hubungan).
    public UserEnt register(UserEnt user) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            // Cek apakah username atau email sudah pernah dipakai
            String checkSql = "SELECT id_user FROM tb_user WHERE username = ? OR email = ? LIMIT 1";
            ps = conn.prepareStatement(checkSql);
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getEmail());
            rs = ps.executeQuery();
            if (rs.next()) {
                return null; // Username or email already exists
            }
            closeResource(rs);
            closeResource(ps);

            // Tentukan role pengguna
            String role = user.getRole();
            if (role == null || role.trim().isEmpty()) {
                role = "KELUARGA";
            } else {
                role = role.trim().toUpperCase();
                if (!role.equals("KELUARGA") && !role.equals("PENGGUNA_BELT") && !role.equals("LANSIA") && !role.equals("CAREGIVER") && !role.equals("ADMIN")) {
                    role = "KELUARGA";
                }
            }

            int statusAktif = (user.getStatusAktif() != null && user.getStatusAktif()) ? 1 : 0;

            // Enkripsi kata sandi menggunakan BCrypt
            String hash = BCrypt.hashpw(user.getPassword(), BCrypt.gensalt(10));
            String insertSql = "INSERT INTO tb_user (nama_lengkap, email, username, password_hash, role, no_telepon, telegram_chat_id, email_verified, status_aktif, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, NOW())";
            ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, user.getNamaLengkap());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getUsername());
            ps.setString(4, hash);
            ps.setString(5, role);
            ps.setString(6, user.getNoTelepon());
            ps.setString(7, user.getTelegramChatId());
            ps.setInt(8, statusAktif);
            ps.executeUpdate();

            // Dapatkan auto-increment ID user yang baru terbuat
            rs = ps.getGeneratedKeys();
            if (rs.next()) {
                user.setIdUser(rs.getInt(1));
            }

            // AUTO-INSERT PENGHUNI JIKA ROLE PENGGUNA_BELT / LANSIA
            if ("PENGGUNA_BELT".equalsIgnoreCase(role) || "LANSIA".equalsIgnoreCase(role)) {
                PreparedStatement psPenghuni = null;
                try {
                    String insertPenghuniSql = "INSERT INTO tb_penghuni (id_user_lansia, nama_lengkap, tanggal_lahir, jenis_kelamin, alamat_kamar, no_telepon, riwayat_penyakit, status_aktif, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())";
                    psPenghuni = conn.prepareStatement(insertPenghuniSql);
                    psPenghuni.setInt(1, user.getIdUser());
                    psPenghuni.setString(2, user.getNamaLengkap());
                    if (user.getTanggalLahir() != null && !user.getTanggalLahir().trim().isEmpty()) {
                        psPenghuni.setString(3, user.getTanggalLahir().trim());
                    } else {
                        psPenghuni.setNull(3, java.sql.Types.DATE);
                    }
                    psPenghuni.setString(4, (user.getJenisKelamin() != null && user.getJenisKelamin().equalsIgnoreCase("P")) ? "P" : "L");
                    psPenghuni.setString(5, (user.getAlamatKamar() != null && !user.getAlamatKamar().trim().isEmpty()) ? user.getAlamatKamar().trim() : "Kamar Wisma");
                    psPenghuni.setString(6, user.getNoTelepon());
                    psPenghuni.setString(7, user.getRiwayatPenyakit() != null ? user.getRiwayatPenyakit().trim() : null);
                    psPenghuni.setInt(8, statusAktif);
                    psPenghuni.executeUpdate();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    closeResource(psPenghuni);
                }
            }

            // INSERT RELASI KELUARGA JIKA ROLE KELUARGA
            if ("KELUARGA".equalsIgnoreCase(role) && user.getIdPenghuniTarget() != null && user.getIdPenghuniTarget() > 0) {
                PreparedStatement psKeluarga = null;
                try {
                    String hub = (user.getHubunganKeluarga() != null && !user.getHubunganKeluarga().trim().isEmpty())
                            ? user.getHubunganKeluarga().trim() : "Keluarga";
                    String sqlKp = "INSERT INTO tb_keluarga_penghuni (id_penghuni, id_user, hubungan, created_at) VALUES (?, ?, ?, NOW()) " +
                            "ON DUPLICATE KEY UPDATE hubungan = VALUES(hubungan)";
                    psKeluarga = conn.prepareStatement(sqlKp);
                    psKeluarga.setInt(1, user.getIdPenghuniTarget());
                    psKeluarga.setInt(2, user.getIdUser());
                    psKeluarga.setString(3, hub);
                    psKeluarga.executeUpdate();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    closeResource(psKeluarga);
                }
            }

            user.setRole(role);
            user.setStatusAktif(statusAktif == 1);
            user.setPassword(null);
            return user;
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: approveUser(idUser)
    // ------------------------------------------------------------------------
    // Fungsi:
    // 1. Mengubah status_aktif dari 0 menjadi 1 pada 'tb_user' berdasarkan id_user.
    // 2. Jika akun tersebut bertipe lansia (terhubung dengan 'tb_penghuni'), aktifkan
    //    juga status_aktif = 1 pada record di 'tb_penghuni' agar lansia muncul di sistem.
    public boolean approveUser(int idUser) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        PreparedStatement psPenghuni = null;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("UPDATE tb_user SET status_aktif = 1 WHERE id_user = ?");
            ps.setInt(1, idUser);
            int rows = ps.executeUpdate();

            // Aktifkan juga data lansia di tb_penghuni jika terkait
            try {
                psPenghuni = conn.prepareStatement("UPDATE tb_penghuni SET status_aktif = 1 WHERE id_user_lansia = ?");
                psPenghuni.setInt(1, idUser);
                psPenghuni.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }

            return rows > 0;
        } finally {
            closeResource(psPenghuni);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: getUserById(idUser)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Mengambil satu data pengguna secara lengkap dari 'tb_user' berdasarkan Primary Key id_user.
    // Dipakai saat aplikasi membutuhkan data profil spesifik user yang sedang login.
    public UserEnt getUserById(int idUser) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("SELECT * FROM tb_user WHERE id_user = ?");
            ps.setInt(1, idUser);
            rs = ps.executeQuery();
            if (rs.next()) {
                return mapUser(rs);
            }
            return null;
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: getAllUsers(roleFilter)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Mengambil daftar seluruh user dari 'tb_user'.
    // Parameter roleFilter bersifat opsional: jika diisi (misal: 'KELUARGA' atau 'CAREGIVER'),
    // query akan memfilter berdasarkan role tersebut. Jika kosong/null, mengambil semua akun.
    public List<UserEnt> getAllUsers(String roleFilter) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        List<UserEnt> list = new ArrayList<>();
        try {
            conn = getConnection();
            String sql = "SELECT * FROM tb_user";
            if (roleFilter != null && !roleFilter.isEmpty()) {
                sql += " WHERE role = ? ORDER BY id_user DESC";
                ps = conn.prepareStatement(sql);
                ps.setString(1, roleFilter);
            } else {
                sql += " ORDER BY id_user DESC";
                ps = conn.prepareStatement(sql);
            }
            rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapUser(rs));
            }
            return list;
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: updateUser(user)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Memperbarui atribut profil pengguna (nama_lengkap, email, no_telepon, telegram_chat_id)
    // pada 'tb_user' berdasarkan id_user.
    public boolean updateUser(UserEnt user) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = getConnection();
            String sql = "UPDATE tb_user SET nama_lengkap = ?, email = ?, no_telepon = ?, telegram_chat_id = ? WHERE id_user = ?";
            ps = conn.prepareStatement(sql);
            ps.setString(1, user.getNamaLengkap());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getNoTelepon());
            ps.setString(4, user.getTelegramChatId());
            ps.setInt(5, user.getIdUser());
            return ps.executeUpdate() > 0;
        } finally {
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: deactivateUser(idUser)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Menonaktifkan akun pengguna (Soft-Delete) dengan mengubah status_aktif = 0.
    // Pengguna yang non-aktif tidak akan bisa login ke sistem lagi.
    public boolean deactivateUser(int idUser) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("UPDATE tb_user SET status_aktif = 0 WHERE id_user = ?");
            ps.setInt(1, idUser);
            return ps.executeUpdate() > 0;
        } finally {
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: forgotPassword(usernameOrEmail)
    // ------------------------------------------------------------------------
    // Fungsi:
    // 1. Memeriksa keberadaan pengguna aktif berdasarkan username atau email.
    // 2. Jika valid, menghasilkan kode token OTP 6-digit acak (100000 - 999999).
    // 3. Menyimpan token ke 'tb_reset_password' dengan masa berlaku (expired) 30 menit.
    // 4. Mengirimkan email HTML otomatis berisi token tersebut via EmailHelper (SMTP Gmail).
    // 5. Mengembalikan nilai token (untuk keperluan logging / pengujian).
    public String forgotPassword(String usernameOrEmail) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("SELECT id_user, email, nama_lengkap FROM tb_user WHERE (username = ? OR email = ?) AND status_aktif = 1 LIMIT 1");
            ps.setString(1, usernameOrEmail);
            ps.setString(2, usernameOrEmail);
            rs = ps.executeQuery();

            if (rs.next()) {
                int idUser = rs.getInt("id_user");
                String email = rs.getString("email");
                String nama = rs.getString("nama_lengkap");

                // Generate token 6-digit acak
                int code = 100000 + new Random().nextInt(900000);
                String token = String.valueOf(code);

                PreparedStatement psToken = conn.prepareStatement(
                        "INSERT INTO tb_reset_password (id_user, token, expired_at, is_used, created_at) " +
                                "VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 30 MINUTE), 0, NOW())"
                );
                psToken.setInt(1, idUser);
                psToken.setString(2, token);
                psToken.executeUpdate();
                closeResource(psToken);

                // Kirim email notifikasi kode OTP
                String subject = "Kode Verifikasi Reset Password Smart-Belt";
                String body = "<h3>Halo, " + nama + "</h3>" +
                        "<p>Kode verifikasi reset password Anda adalah:</p>" +
                        "<h1 style='color:#2b7a78; font-size: 32px; letter-spacing: 5px;'>" + token + "</h1>" +
                        "<p>Kode ini berlaku selama 30 menit. Jangan bagikan kode ini kepada siapa pun.</p>";
                EmailHelper.sendEmailAsync(email, subject, body);

                return token;
            }
            return null;
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: resetPassword(token, newPassword)
    // ------------------------------------------------------------------------
    // Fungsi:
    // 1. Memeriksa validitas token reset pada 'tb_reset_password' (is_used = 0 dan belum kadaluarsa).
    // 2. Melakukan enkripsi password baru menggunakan BCrypt.hashpw(..., BCrypt.gensalt(10)).
    // 3. Mengupdate kolom 'password_hash' pada tabel 'tb_user'.
    // 4. Menandai token sebagai hangus (is_used = 1) agar tidak dapat disalahgunakan kembali.
    public boolean resetPassword(String token, String newPassword) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("SELECT id_reset, id_user FROM tb_reset_password WHERE token = ? AND is_used = 0 AND expired_at > NOW() LIMIT 1");
            ps.setString(1, token);
            rs = ps.executeQuery();

            if (rs.next()) {
                int idReset = rs.getInt("id_reset");
                int idUser = rs.getInt("id_user");

                String hash = BCrypt.hashpw(newPassword, BCrypt.gensalt(10));
                PreparedStatement psUpUser = conn.prepareStatement("UPDATE tb_user SET password_hash = ? WHERE id_user = ?");
                psUpUser.setString(1, hash);
                psUpUser.setInt(2, idUser);
                psUpUser.executeUpdate();
                closeResource(psUpUser);

                PreparedStatement psUpToken = conn.prepareStatement("UPDATE tb_reset_password SET is_used = 1 WHERE id_reset = ?");
                psUpToken.setInt(1, idReset);
                psUpToken.executeUpdate();
                closeResource(psUpToken);

                return true;
            }
            return false;
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: verifyResetToken(token)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Memverifikasi apakah kode token reset password masih aktif (belum expired dan belum dipakai)
    // sebelum pengguna dialihkan ke halaman formulir ganti password di aplikasi.
    public boolean verifyResetToken(String token) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("SELECT id_reset FROM tb_reset_password WHERE token = ? AND is_used = 0 AND expired_at > NOW() LIMIT 1");
            ps.setString(1, token);
            rs = ps.executeQuery();
            return rs.next();
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ========================================================================
    // 2. MODUL DATA PENGHUNI (LANSIA) & KONTAK DARURAT (tb_penghuni & tb_kontak_darurat)
    // ========================================================================

    // ------------------------------------------------------------------------
    // METHOD: getAllPenghuni()
    // ------------------------------------------------------------------------
    // Fungsi:
    // Mengambil seluruh data lansia yang terdaftar di panti wisma dari 'tb_penghuni',
    // di-JOIN dengan 'tb_perangkat' untuk mengetahui sabuk Smart-Belt mana yang sedang
    // dipasangkan ke masing-masing lansia.
    // Hasil diurutkan berdasarkan status aktif terlebih dahulu, lalu nama lansia (A-Z).
    public List<PenghuniEnt> getAllPenghuni() throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        List<PenghuniEnt> list = new ArrayList<>();
        try {
            conn = getConnection();
            String sql = "SELECT p.*, d.id_perangkat, d.nama_perangkat, d.mac_address " +
                    "FROM tb_penghuni p " +
                    "LEFT JOIN tb_perangkat d ON p.id_penghuni = d.id_penghuni " +
                    "ORDER BY p.status_aktif DESC, p.nama_lengkap ASC";
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapPenghuni(rs));
            }
            return list;
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: getPenghuniById(idPenghuni)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Mengambil data detail satu lansia tertentu berdasarkan Primary Key 'id_penghuni',
    // lengkap beserta data perangkat sabuk IoT yang terpasang (jika ada).
    public PenghuniEnt getPenghuniById(int idPenghuni) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            String sql = "SELECT p.*, d.id_perangkat, d.nama_perangkat, d.mac_address " +
                    "FROM tb_penghuni p " +
                    "LEFT JOIN tb_perangkat d ON p.id_penghuni = d.id_penghuni " +
                    "WHERE p.id_penghuni = ?";
            ps = conn.prepareStatement(sql);
            ps.setInt(1, idPenghuni);
            rs = ps.executeQuery();
            if (rs.next()) {
                return mapPenghuni(rs);
            }
            return null;
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: addPenghuni(p)
    // ------------------------------------------------------------------------
    // Fungsi:
    // 1. Menyimpan data lansia baru ke tabel 'tb_penghuni' (nama, tanggal lahir, jenis kelamin,
    //    nomor kamar wisma, nomor telepon, dan riwayat diagnosa penyakit).
    // 2. Mengambil auto-increment primary key ID baru (RETURN_GENERATED_KEYS).
    // 3. Jika pengguna memilih perangkat sabuk pintar (idPerangkat), sistem otomatis
    //    memasangkan (assign) sabuk tersebut ke lansia bersangkutan pada 'tb_perangkat'.
    public PenghuniEnt addPenghuni(PenghuniEnt p) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        PreparedStatement psDev = null;
        try {
            conn = getConnection();
            String sql = "INSERT INTO tb_penghuni (id_user, id_user_lansia, nama_lengkap, tanggal_lahir, jenis_kelamin, alamat_kamar, no_telepon, riwayat_penyakit, status_aktif, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";
            ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            if (p.getIdUser() != null && p.getIdUser() > 0) ps.setInt(1, p.getIdUser()); else ps.setNull(1, java.sql.Types.INTEGER);
            if (p.getIdUserLansia() != null && p.getIdUserLansia() > 0) ps.setInt(2, p.getIdUserLansia()); else ps.setNull(2, java.sql.Types.INTEGER);
            ps.setString(3, p.getNamaLengkap());
            if (p.getTanggalLahir() != null && !p.getTanggalLahir().trim().isEmpty()) ps.setString(4, p.getTanggalLahir().trim()); else ps.setNull(4, java.sql.Types.DATE);
            ps.setString(5, (p.getJenisKelamin() != null && p.getJenisKelamin().equalsIgnoreCase("P")) ? "P" : "L");
            ps.setString(6, p.getAlamatKamar() != null ? p.getAlamatKamar().trim() : "Kamar Wisma");
            ps.setString(7, p.getNoTelepon());
            ps.setString(8, p.getRiwayatPenyakit());
            ps.setInt(9, (p.getStatusAktif() != null && !p.getStatusAktif()) ? 0 : 1);
            ps.executeUpdate();

            rs = ps.getGeneratedKeys();
            if (rs.next()) {
                p.setIdPenghuni(rs.getInt(1));
            }

            // Pasangkan ke smart-belt jika idPerangkat dipilih
            if (p.getIdPerangkat() != null && p.getIdPerangkat() > 0 && p.getIdPenghuni() != null) {
                psDev = conn.prepareStatement("UPDATE tb_perangkat SET id_penghuni = ? WHERE id_perangkat = ?");
                psDev.setInt(1, p.getIdPenghuni());
                psDev.setInt(2, p.getIdPerangkat());
                psDev.executeUpdate();
            }

            return p;
        } finally {
            closeResource(psDev);
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: updatePenghuni(p)
    // ------------------------------------------------------------------------
    // Fungsi:
    // 1. Memperbarui informasi personal lansia pada tabel 'tb_penghuni'.
    // 2. Mengatur ulang pemetaan (re-assign) sabuk pintar:
    //    - Melepas sabuk lama milik lansia ini (id_penghuni = NULL).
    //    - Memasangkan sabuk baru yang dipilih (jika ada).
    public boolean updatePenghuni(PenghuniEnt p) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        PreparedStatement psDevClear = null;
        PreparedStatement psDevSet = null;
        try {
            conn = getConnection();
            String sql = "UPDATE tb_penghuni SET nama_lengkap = ?, tanggal_lahir = ?, jenis_kelamin = ?, alamat_kamar = ?, no_telepon = ?, riwayat_penyakit = ?, status_aktif = ?, id_user_lansia = ?, id_user = ? WHERE id_penghuni = ?";
            ps = conn.prepareStatement(sql);
            ps.setString(1, p.getNamaLengkap());
            if (p.getTanggalLahir() != null && !p.getTanggalLahir().trim().isEmpty()) ps.setString(2, p.getTanggalLahir().trim()); else ps.setNull(2, java.sql.Types.DATE);
            ps.setString(3, (p.getJenisKelamin() != null && p.getJenisKelamin().equalsIgnoreCase("P")) ? "P" : "L");
            ps.setString(4, p.getAlamatKamar() != null ? p.getAlamatKamar().trim() : "Kamar Wisma");
            ps.setString(5, p.getNoTelepon());
            ps.setString(6, p.getRiwayatPenyakit());
            ps.setInt(7, (p.getStatusAktif() != null && p.getStatusAktif()) ? 1 : 0);
            if (p.getIdUserLansia() != null && p.getIdUserLansia() > 0) ps.setInt(8, p.getIdUserLansia()); else ps.setNull(8, java.sql.Types.INTEGER);
            if (p.getIdUser() != null && p.getIdUser() > 0) ps.setInt(9, p.getIdUser()); else ps.setNull(9, java.sql.Types.INTEGER);
            ps.setInt(10, p.getIdPenghuni());
            int rows = ps.executeUpdate();

            // Lepaskan perangkat lama milik penghuni ini
            psDevClear = conn.prepareStatement("UPDATE tb_perangkat SET id_penghuni = NULL WHERE id_penghuni = ?");
            psDevClear.setInt(1, p.getIdPenghuni());
            psDevClear.executeUpdate();

            // Pasangkan perangkat baru jika dipilih
            if (p.getIdPerangkat() != null && p.getIdPerangkat() > 0) {
                psDevSet = conn.prepareStatement("UPDATE tb_perangkat SET id_penghuni = ? WHERE id_perangkat = ?");
                psDevSet.setInt(1, p.getIdPenghuni());
                psDevSet.setInt(2, p.getIdPerangkat());
                psDevSet.executeUpdate();
            }

            return rows > 0;
        } finally {
            closeResource(psDevSet);
            closeResource(psDevClear);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: deletePenghuni(idPenghuni)
    // ------------------------------------------------------------------------
    // Fungsi:
    // 1. Melepaskan tautan perangkat sabuk yang dipakai lansia (id_penghuni = NULL).
    // 2. Melakukan Soft-Delete dengan mengubah status_aktif = 0 pada 'tb_penghuni',
    //    sehingga riwayat insiden dan log sensor masa lampau tetap utuh untuk arsip medis.
    public boolean deletePenghuni(int idPenghuni) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        PreparedStatement psDev = null;
        try {
            conn = getConnection();
            // Lepaskan perangkat dari penghuni
            psDev = conn.prepareStatement("UPDATE tb_perangkat SET id_penghuni = NULL WHERE id_penghuni = ?");
            psDev.setInt(1, idPenghuni);
            psDev.executeUpdate();

            // Soft delete
            ps = conn.prepareStatement("UPDATE tb_penghuni SET status_aktif = 0 WHERE id_penghuni = ?");
            ps.setInt(1, idPenghuni);
            return ps.executeUpdate() > 0;
        } finally {
            closeResource(psDev);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: getKeluargaByUserId(userId)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Mencari informasi relasi keluarga antara akun pengguna (tb_user) dengan lansia
    // yang dirawat (tb_penghuni) melalui tabel penghubung 'tb_keluarga_penghuni'.
    public Map<String, Object> getKeluargaByUserId(int userId) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            String sql = "SELECT kp.id_penghuni, p.nama_lengkap, p.alamat_kamar, kp.hubungan " +
                    "FROM tb_keluarga_penghuni kp " +
                    "JOIN tb_penghuni p ON kp.id_penghuni = p.id_penghuni " +
                    "WHERE kp.id_user = ? LIMIT 1";
            ps = conn.prepareStatement(sql);
            ps.setInt(1, userId);
            rs = ps.executeQuery();
            if (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("idPenghuni", rs.getInt("id_penghuni"));
                map.put("namaLengkap", rs.getString("nama_lengkap"));
                map.put("alamatKamar", rs.getString("alamat_kamar"));
                map.put("hubungan", rs.getString("hubungan"));
                return map;
            }
            return null;
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: setKeluargaPenghuni(userId, idPenghuni, hubungan)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Menghubungkan akun keluarga (tb_user) dengan profil lansia yang dirawat (tb_penghuni).
    // Prosedur: menghapus asosiasi lama terlebih dahulu, lalu memasukkan record baru
    // ke 'tb_keluarga_penghuni'.
    public boolean setKeluargaPenghuni(int userId, int idPenghuni, String hubungan) throws SQLException {
        Connection conn = null;
        PreparedStatement psDel = null;
        PreparedStatement psIns = null;
        try {
            conn = getConnection();
            psDel = conn.prepareStatement("DELETE FROM tb_keluarga_penghuni WHERE id_user = ?");
            psDel.setInt(1, userId);
            psDel.executeUpdate();

            if (idPenghuni > 0) {
                String sql = "INSERT INTO tb_keluarga_penghuni (id_penghuni, id_user, hubungan, created_at) VALUES (?, ?, ?, NOW())";
                psIns = conn.prepareStatement(sql);
                psIns.setInt(1, idPenghuni);
                psIns.setInt(2, userId);
                psIns.setString(3, (hubungan != null && !hubungan.trim().isEmpty()) ? hubungan.trim() : "Keluarga");
                return psIns.executeUpdate() > 0;
            }
            return true;
        } finally {
            closeResource(psIns);
            closeResource(psDel);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: getPenghuniByKeluarga(userId)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Mengambil daftar lansia aktif yang terikat dengan akun keluarga (userId) tertentu.
    // Dipakai saat keluarga login ke aplikasi Android agar aplikasi hanya memunculkan lansia mereka.
    public List<PenghuniEnt> getPenghuniByKeluarga(int userId) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        List<PenghuniEnt> list = new ArrayList<>();
        try {
            conn = getConnection();
            String sql = "SELECT p.*, d.id_perangkat, d.nama_perangkat, d.mac_address FROM tb_penghuni p " +
                    "JOIN tb_keluarga_penghuni kp ON p.id_penghuni = kp.id_penghuni " +
                    "LEFT JOIN tb_perangkat d ON p.id_penghuni = d.id_penghuni " +
                    "WHERE kp.id_user = ? AND p.status_aktif = 1";
            ps = conn.prepareStatement(sql);
            ps.setInt(1, userId);
            rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapPenghuni(rs));
            }
            if (list.isEmpty()) {
                // Fallback: direct id_user check
                closeResource(rs);
                closeResource(ps);
                ps = conn.prepareStatement("SELECT p.*, d.id_perangkat, d.nama_perangkat, d.mac_address FROM tb_penghuni p LEFT JOIN tb_perangkat d ON p.id_penghuni = d.id_penghuni WHERE p.id_user = ? AND p.status_aktif = 1");
                ps.setInt(1, userId);
                rs = ps.executeQuery();
                while (rs.next()) {
                    list.add(mapPenghuni(rs));
                }
            }
            return list;
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    public boolean updatePenghuniPerangkat(int idPenghuni, int idPerangkat) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("UPDATE tb_perangkat SET id_penghuni = ? WHERE id_perangkat = ?");
            ps.setInt(1, idPenghuni);
            ps.setInt(2, idPerangkat);
            return ps.executeUpdate() > 0;
        } finally {
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: getPenghuniByLansia(userId)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Mengambil profil data lansia berdasarkan id_user akun lansia itu sendiri (tb_penghuni.id_user_lansia).
    public PenghuniEnt getPenghuniByLansia(int userId) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("SELECT * FROM tb_penghuni WHERE id_user_lansia = ? AND status_aktif = 1 LIMIT 1");
            ps.setInt(1, userId);
            rs = ps.executeQuery();
            if (rs.next()) {
                return mapPenghuni(rs);
            }
            return null;
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: getKontakByPenghuni(idPenghuni)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Mengambil daftar nomor telepon darurat keluarga dari lansia bersangkutan,
    // diurutkan berdasarkan skala prioritas (prioritas 1 = kontak darurat utama).
    public List<KontakDaruratEnt> getKontakByPenghuni(int idPenghuni) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        List<KontakDaruratEnt> list = new ArrayList<>();
        try {
            conn = getConnection();
            ps = conn.prepareStatement("SELECT * FROM tb_kontak_darurat WHERE id_penghuni = ? AND status_aktif = 1 ORDER BY prioritas ASC");
            ps.setInt(1, idPenghuni);
            rs = ps.executeQuery();
            while (rs.next()) {
                KontakDaruratEnt k = new KontakDaruratEnt();
                k.setIdKontak(rs.getInt("id_kontak"));
                k.setIdPenghuni(rs.getInt("id_penghuni"));
                k.setNamaKontak(rs.getString("nama_kontak"));
                k.setNoTelepon(rs.getString("no_telepon"));
                k.setHubungan(rs.getString("hubungan"));
                k.setPrioritas(rs.getInt("prioritas"));
                k.setStatusAktif(rs.getInt("status_aktif") == 1);
                list.add(k);
            }
            return list;
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: addKontak(idPenghuni, k)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Mendaftarkan nomor kontak darurat baru untuk seorang lansia di 'tb_kontak_darurat'.
    // Nomor inilah yang nantinya dipanggil otomatis oleh fitur Auto-Call darurat di HP Android.
    public KontakDaruratEnt addKontak(int idPenghuni, KontakDaruratEnt k) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            String sql = "INSERT INTO tb_kontak_darurat (id_penghuni, nama_kontak, no_telepon, hubungan, prioritas, status_aktif, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, 1, NOW())";
            ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, idPenghuni);
            ps.setString(2, k.getNamaKontak());
            ps.setString(3, k.getNoTelepon());
            ps.setString(4, k.getHubungan() != null ? k.getHubungan() : "KELUARGA");
            ps.setInt(5, k.getPrioritas() != null ? k.getPrioritas() : 1);
            ps.executeUpdate();
            rs = ps.getGeneratedKeys();
            if (rs.next()) {
                k.setIdKontak(rs.getInt(1));
            }
            k.setIdPenghuni(idPenghuni);
            k.setStatusAktif(true);
            return k;
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: updateKontak(idKontak, k)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Mengubah rincian nama kontak, nomor telepon, hubungan keluarga, atau prioritas panggilan.
    public boolean updateKontak(int idKontak, KontakDaruratEnt k) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = getConnection();
            String sql = "UPDATE tb_kontak_darurat SET nama_kontak = ?, no_telepon = ?, hubungan = ?, prioritas = ? WHERE id_kontak = ?";
            ps = conn.prepareStatement(sql);
            ps.setString(1, k.getNamaKontak());
            ps.setString(2, k.getNoTelepon());
            ps.setString(3, k.getHubungan());
            ps.setInt(4, k.getPrioritas());
            ps.setInt(5, idKontak);
            return ps.executeUpdate() > 0;
        } finally {
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: deleteKontak(idKontak)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Menonaktifkan kontak darurat (status_aktif = 0) agar tidak lagi dihubungi saat darurat.
    public boolean deleteKontak(int idKontak) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("UPDATE tb_kontak_darurat SET status_aktif = 0 WHERE id_kontak = ?");
            ps.setInt(1, idKontak);
            return ps.executeUpdate() > 0;
        } finally {
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ========================================================================
    // 3. MODUL PERANGKAT SABUK IOT & PELACAKAN LOKASI GPS (tb_perangkat)
    // ========================================================================

    // ------------------------------------------------------------------------
    // METHOD: getAllPerangkat()
    // ------------------------------------------------------------------------
    // Fungsi:
    // Mengambil seluruh sabuk pintar yang terdaftar di database wisma.
    // Menampilkan informasi hardware: nama perangkat, nomor seri, MAC address Bluetooth/WiFi,
    // status konektivitas jaringan (ONLINE/OFFLINE), sisa baterai (%), dan waktu detak terakhir.
    public List<PerangkatEnt> getAllPerangkat() throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        List<PerangkatEnt> list = new ArrayList<>();
        try {
            conn = getConnection();
            ps = conn.prepareStatement("SELECT * FROM tb_perangkat ORDER BY id_perangkat ASC");
            rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapPerangkat(rs));
            }
            return list;
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: getPerangkatByMac(macAddress)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Mengidentifikasi dan mengambil data perangkat keras berdasarkan MAC Address
    // perangkat Raspberry Pi Pico W pengirim data.
    public PerangkatEnt getPerangkatByMac(String macAddress) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("SELECT * FROM tb_perangkat WHERE mac_address = ? LIMIT 1");
            ps.setString(1, macAddress);
            rs = ps.executeQuery();
            if (rs.next()) {
                return mapPerangkat(rs);
            }
            return null;
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: getLokasiAktif()
    // ------------------------------------------------------------------------
    // Fungsi:
    // Menggabungkan (JOIN) data perangkat, profil lansia yang memakai, koordinat GPS
    // pembacaan terakhir dari 'tb_data_sensor', serta status darurat terkini.
    // Data ini dikonsumsi oleh layar peta (Google Maps) di aplikasi Android Caregiver
    // untuk menampilkan posisi titik (marker) dan status keselamatan seluruh lansia panti.
    public List<BeltLocationEnt> getLokasiAktif() throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        List<BeltLocationEnt> list = new ArrayList<>();
        try {
            conn = getConnection();
            String sql = "SELECT p.id_perangkat, p.nama_perangkat, p.mac_address, p.status_koneksi, p.status_baterai, " +
                    "pen.id_penghuni, pen.nama_lengkap AS nama_penghuni, pen.alamat_kamar, " +
                    "s.latitude, s.longitude, s.waktu_baca, " +
                    "k.status_kejadian, k.jenis_kejadian " +
                    "FROM tb_perangkat p " +
                    "LEFT JOIN tb_penghuni pen ON p.id_penghuni = pen.id_penghuni " +
                    "LEFT JOIN (SELECT id_perangkat, latitude, longitude, waktu_baca FROM tb_data_sensor WHERE (id_perangkat, waktu_baca) IN (SELECT id_perangkat, MAX(waktu_baca) FROM tb_data_sensor WHERE latitude IS NOT NULL AND latitude != 0 AND latitude != -3.695436 GROUP BY id_perangkat)) s ON p.id_perangkat = s.id_perangkat " +
                    "LEFT JOIN (SELECT id_perangkat, status_kejadian, jenis_kejadian FROM tb_kejadian_darurat WHERE status_kejadian IN ('TERDETEKSI','COUNTDOWN','AUTO_CALL') ORDER BY id_kejadian DESC LIMIT 1) k ON p.id_perangkat = k.id_perangkat " +
                    "ORDER BY (s.waktu_baca IS NOT NULL) DESC, s.waktu_baca DESC, p.id_perangkat ASC";

            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            while (rs.next()) {
                BeltLocationEnt b = new BeltLocationEnt();
                b.setIdPerangkat(rs.getInt("id_perangkat"));
                b.setNamaPerangkat(rs.getString("nama_perangkat"));
                b.setMacAddress(rs.getString("mac_address"));
                b.setStatusKoneksi(rs.getInt("status_koneksi") == 1);
                b.setStatusBaterai(rs.getInt("status_baterai"));
                b.setIdPenghuni(rs.getInt("id_penghuni"));
                b.setNamaPenghuni(rs.getString("nama_penghuni"));
                b.setAlamatKamar(rs.getString("alamat_kamar"));
                b.setLatitude(rs.getDouble("latitude"));
                b.setLongitude(rs.getDouble("longitude"));
                b.setWaktuTerakhir(rs.getString("waktu_baca"));
                b.setStatusDarurat(rs.getString("status_kejadian"));
                b.setJenisKejadianTerakhir(rs.getString("jenis_kejadian"));
                list.add(b);
            }
            return list;
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ========================================================================
    // 2. MODUL IOT TELEMETRI & PENANGANAN DARURAT (tb_data_sensor & tb_kejadian_darurat)
    // ========================================================================

    // ------------------------------------------------------------------------
    // METHOD: simpanTelemetry(req)
    // ------------------------------------------------------------------------
    // Fungsi:
    // 1. Menerima data telemetri berkala yang dikirim Pico W via HTTP POST.
    // 2. Mencocokkan MAC address untuk memperoleh id_perangkat.
    // 3. Menyimpan record histori baru ke 'tb_data_sensor' (akselerasi, gyro, SVM,
    //    sudut pitch & roll, detak jantung BPM, saturasi oksigen SpO2, dan koordinat GPS).
    // 4. Memperbarui status perangkat di 'tb_perangkat' (status_koneksi=1, status_baterai,
    //    dan terakhir_terhubung = NOW()).
    public boolean simpanTelemetry(TelemetryRequestEnt req) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = getConnection();
            PerangkatEnt dev = getPerangkatByMac(req.getMacAddress());
            int idPerangkat = (dev != null) ? dev.getIdPerangkat() : 1;

            String sql = "INSERT INTO tb_data_sensor (id_perangkat, accel_x, accel_y, accel_z, gyro_x, gyro_y, gyro_z, svm, pitch, roll, heart_rate, spo2, latitude, longitude, waktu_baca) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";
            ps = conn.prepareStatement(sql);
            ps.setInt(1, idPerangkat);
            ps.setObject(2, req.getAccelX());
            ps.setObject(3, req.getAccelY());
            ps.setObject(4, req.getAccelZ());
            ps.setObject(5, req.getGyroX());
            ps.setObject(6, req.getGyroY());
            ps.setObject(7, req.getGyroZ());
            ps.setObject(8, req.getSvm());
            ps.setObject(9, req.getPitch());
            ps.setObject(10, req.getRoll());
            ps.setObject(11, req.getHeartRate());
            ps.setObject(12, req.getSpo2());
            ps.setObject(13, req.getLatitude());
            ps.setObject(14, req.getLongitude());
            int inserted = ps.executeUpdate();
            closeResource(ps);

            // Update status perangkat (heartbeat & level baterai)
            PreparedStatement psDev = conn.prepareStatement("UPDATE tb_perangkat SET status_koneksi = 1, status_baterai = ?, terakhir_terhubung = NOW() WHERE id_perangkat = ?");
            psDev.setInt(1, req.getStatusBaterai() != null ? req.getStatusBaterai() : 100);
            psDev.setInt(2, idPerangkat);
            psDev.executeUpdate();
            closeResource(psDev);

            return inserted > 0;
        } finally {
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: triggerEmergency(req)
    // ------------------------------------------------------------------------
    // Fungsi:
    // 1. Menerima trigger insiden dari sabuk Pico W (JATUH, PANIC_BUTTON, TILT_EKSTREM, dll).
    // 2. Mengambil data lansia & perangkat terkait.
    // 3. Menyimpan insiden ke 'tb_kejadian_darurat' dengan koordinat GPS dan status_kejadian.
    // 4. Mengirimkan notifikasi darurat instan ke bot Telegram perawat/caregiver via TelegramHelper.
    // 5. Mengembalikan objek KejadianDaruratEnt lengkap dengan durasi countdown buzzer.
    public KejadianDaruratEnt triggerEmergency(EmergencyRequestEnt req) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            PerangkatEnt dev = getPerangkatByMac(req.getMacAddress());
            int idPerangkat = (dev != null) ? dev.getIdPerangkat() : 1;
            int idPenghuni = (dev != null && dev.getIdPenghuni() != null) ? dev.getIdPenghuni() : 1;

            PenghuniEnt pen = getPenghuniById(idPenghuni);
            String namaPenghuni = (pen != null) ? pen.getNamaLengkap() : "Lansia Panti";
            String alamatKamar = (pen != null) ? pen.getAlamatKamar() : "Wisma Panti Lansia";

            // Fallback koordinat default lokasi wisma jika GPS sabuk belum lock sinyal satelit
            double lat = req.getLatitude() != null ? req.getLatitude() : -3.695436;
            double lng = req.getLongitude() != null ? req.getLongitude() : 128.181427;
            String lokasi = req.getAlamatLokasi() != null ? req.getAlamatLokasi() : "Wisma Panti Lansia";

            // Ambil setting durasi countdown dari tb_pengaturan (default 30 detik)
            PengaturanEnt cfg = getPengaturan();
            int countdown = (cfg != null && cfg.getDurasiCountdown() != null) ? cfg.getDurasiCountdown() : 30;

            // Check if there is an active incident for this device already
            String checkActiveSql = "SELECT id_kejadian FROM tb_kejadian_darurat WHERE id_perangkat = ? AND status_kejadian IN ('TERDETEKSI','COUNTDOWN','AUTO_CALL') ORDER BY id_kejadian DESC LIMIT 1";
            ps = conn.prepareStatement(checkActiveSql);
            ps.setInt(1, idPerangkat);
            rs = ps.executeQuery();
            int existingId = 0;
            if (rs.next()) {
                existingId = rs.getInt("id_kejadian");
            }
            closeResource(rs);
            closeResource(ps);

            String targetStatus = req.getStatusKejadian() != null ? req.getStatusKejadian() : "AUTO_CALL";
            int activeId = existingId;

            if (existingId > 0) {
                String updateSql = "UPDATE tb_kejadian_darurat SET status_kejadian = ? WHERE id_kejadian = ?";
                ps = conn.prepareStatement(updateSql);
                ps.setString(1, targetStatus);
                ps.setInt(2, existingId);
                ps.executeUpdate();
                closeResource(ps);
            } else {
                String sql = "INSERT INTO tb_kejadian_darurat (id_penghuni, id_perangkat, jenis_kejadian, svm_value, pitch_value, roll_value, accel_x, accel_y, accel_z, latitude, longitude, alamat_lokasi, status_kejadian, waktu_kejadian, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";
                ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setInt(1, idPenghuni);
                ps.setInt(2, idPerangkat);
                ps.setString(3, req.getJenisKejadian() != null ? req.getJenisKejadian() : "JATUH");
                ps.setObject(4, req.getSvmValue());
                ps.setObject(5, req.getPitchValue());
                ps.setObject(6, req.getRollValue());
                ps.setObject(7, req.getAccelX());
                ps.setObject(8, req.getAccelY());
                ps.setObject(9, req.getAccelZ());
                ps.setDouble(10, lat);
                ps.setDouble(11, lng);
                ps.setString(12, lokasi);
                ps.setString(13, targetStatus);
                ps.executeUpdate();
                rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    activeId = rs.getInt(1);
                }
                closeResource(rs);
                closeResource(ps);
            }

            KejadianDaruratEnt kejadian = new KejadianDaruratEnt();
            kejadian.setIdKejadian(activeId);
            kejadian.setPenghuni(pen);
            kejadian.setPerangkat(dev);
            kejadian.setJenisKejadian(req.getJenisKejadian());
            kejadian.setSvmValue(req.getSvmValue());
            kejadian.setPitchValue(req.getPitchValue());
            kejadian.setRollValue(req.getRollValue());
            kejadian.setLatitude(lat);
            kejadian.setLongitude(lng);
            kejadian.setAlamatLokasi(lokasi);
            kejadian.setStatusKejadian(req.getStatusKejadian());
            kejadian.setDurasiCountdown(countdown);
            kejadian.setWaktuKejadian(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

            // Format dan kirim pesan alert Telegram darurat ke perawat
            String mapsUrl = "https://www.google.com/maps?q=" + lat + "," + lng;
            String jenis = req.getJenisKejadian() != null ? req.getJenisKejadian() : "JATUH";
            String headerTitle = switch (jenis) {
                case "HEART_RATE_RENDAH" -> "⚠️ <b>PERINGATAN DETAK JANTUNG RENDAH!</b> ⚠️";
                case "SPO2_RENDAH" -> "⚠️ <b>PERINGATAN SATURASI OKSIGEN RENDAH!</b> ⚠️";
                case "KELUAR_ZONA_AMAN" -> "🚨 <b>PERINGATAN KELUAR ZONA AMAN!</b> 🚨";
                case "PANIC_BUTTON" -> "🚨 <b>PANGGILAN SOS DARURAT!</b> 🚨";
                case "TILT_EKSTREM" -> "🔄 <b>PERINGATAN BADAN MIRING EKSTREM!</b> 🔄";
                case "POSISI_MIRING_LAMA" -> "🔄 <b>PERINGATAN POSISI MIRING LAMA!</b> 🔄";
                default -> "🚨 <b>PERINGATAN DARURAT JATUH!</b> 🚨";
            };

            String telegramMsg = headerTitle + "\n\n" +
                    "👤 <b>Lansia:</b> " + namaPenghuni + "\n" +
                    "🚪 <b>Kamar:</b> " + alamatKamar + "\n" +
                    "📌 <b>Jenis:</b> " + jenis + "\n" +
                    "📍 <b>Lokasi:</b> " + lokasi + "\n" +
                    "🗺️ <a href=\"" + mapsUrl + "\">Buka Google Maps</a>\n\n" +
                    "<i>Harap perawat/caregiver segera memeriksa kondisi lansia!</i>";
            TelegramHelper.sendAlertAsync(telegramMsg);

            return kejadian;
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: getActiveEmergencies()
    // ------------------------------------------------------------------------
    // Fungsi:
    // Mengambil seluruh rekaman darurat yang belum tuntas ditangani
    // (status: 'TERDETEKSI', 'COUNTDOWN', 'AUTO_CALL', 'MENUNGGU').
    public List<KejadianDaruratEnt> getActiveEmergencies() throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        List<KejadianDaruratEnt> list = new ArrayList<>();
        try {
            conn = getConnection();
            String sql = "SELECT k.*, p.nama_lengkap AS nama_penghuni, p.alamat_kamar, dev.nama_perangkat, dev.mac_address " +
                    "FROM tb_kejadian_darurat k " +
                    "JOIN tb_penghuni p ON k.id_penghuni = p.id_penghuni " +
                    "JOIN tb_perangkat dev ON k.id_perangkat = dev.id_perangkat " +
                    "WHERE k.status_kejadian IN ('TERDETEKSI','COUNTDOWN','AUTO_CALL','MENUNGGU') " +
                    "ORDER BY k.id_kejadian DESC";
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapKejadianWithRelations(rs));
            }
            return list;
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: cancelEmergency(macAddress)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Mengubah status_kejadian menjadi 'DIBATALKAN' pada 'tb_kejadian_darurat'.
    // Dipanggil saat tombol GP18 pada Pico W ditekan untuk membatalkan false alarm,
    // atau tombol pembatalan ditekan oleh perawat di aplikasi Android.
    public boolean cancelEmergency(String macAddress) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            String selectSql = "SELECT k.id_kejadian, k.status_kejadian FROM tb_kejadian_darurat k " +
                    "JOIN tb_perangkat p ON k.id_perangkat = p.id_perangkat " +
                    "WHERE (LOWER(p.mac_address) = LOWER(?) OR ? IS NULL OR ? = '') AND k.status_kejadian IN ('TERDETEKSI','COUNTDOWN','AUTO_CALL','MENUNGGU') " +
                    "ORDER BY k.id_kejadian DESC";
            ps = conn.prepareStatement(selectSql);
            String mac = (macAddress != null) ? macAddress.trim() : "";
            ps.setString(1, mac);
            ps.setString(2, mac);
            ps.setString(3, mac);
            rs = ps.executeQuery();
            List<Integer> countdownIds = new ArrayList<>();
            List<Integer> autoCallIds = new ArrayList<>();
            while (rs.next()) {
                int idK = rs.getInt("id_kejadian");
                String st = rs.getString("status_kejadian");
                if ("AUTO_CALL".equalsIgnoreCase(st) || "MENUNGGU".equalsIgnoreCase(st)) {
                    autoCallIds.add(idK);
                } else {
                    countdownIds.add(idK);
                }
            }
            closeResource(rs);
            closeResource(ps);

            // Fallback: Jika tidak ditemukan pencocokan MAC Address spesifik, batalkan insiden darurat aktif paling baru di sistem
            if (countdownIds.isEmpty() && autoCallIds.isEmpty()) {
                String fallbackSql = "SELECT id_kejadian, status_kejadian FROM tb_kejadian_darurat WHERE status_kejadian IN ('TERDETEKSI','COUNTDOWN','AUTO_CALL','MENUNGGU') ORDER BY id_kejadian DESC LIMIT 1";
                ps = conn.prepareStatement(fallbackSql);
                rs = ps.executeQuery();
                if (rs.next()) {
                    int idK = rs.getInt("id_kejadian");
                    String st = rs.getString("status_kejadian");
                    if ("AUTO_CALL".equalsIgnoreCase(st) || "MENUNGGU".equalsIgnoreCase(st)) {
                        autoCallIds.add(idK);
                    } else {
                        countdownIds.add(idK);
                    }
                }
                closeResource(rs);
                closeResource(ps);
            }

            boolean updatedAny = false;
            if (!countdownIds.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < countdownIds.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(countdownIds.get(i));
                }
                String sql1 = "UPDATE tb_kejadian_darurat SET status_kejadian = 'DIBATALKAN', waktu_respon = NOW(), catatan = 'Dibatalkan pengguna selama hitung mundur' WHERE id_kejadian IN (" + sb.toString() + ")";
                ps = conn.prepareStatement(sql1);
                ps.executeUpdate();
                closeResource(ps);
                updatedAny = true;
            }
            if (!autoCallIds.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < autoCallIds.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(autoCallIds.get(i));
                }
                String sql2 = "UPDATE tb_kejadian_darurat SET status_kejadian = 'DIBATALKAN', waktu_respon = NOW(), catatan = 'BUZZER DIMATIKAN: Buzzer dimatikan pengguna setelah hitung mundur selesai' WHERE id_kejadian IN (" + sb.toString() + ")";
                ps = conn.prepareStatement(sql2);
                ps.executeUpdate();
                closeResource(ps);
                updatedAny = true;
            }
            return updatedAny;
        } finally {
            closeResource(rs);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: getLatestSensorData(idPerangkat)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Mengambil 1 baris telemetri paling mutakhir (terakhir) dari 'tb_data_sensor'
    // untuk perangkat tertentu (BPM, SpO2, SVM, sudut badan, dan koordinat GPS).
    public DataSensorEnt getLatestSensorData(int idPerangkat) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("SELECT * FROM tb_data_sensor WHERE id_perangkat = ? ORDER BY id_data DESC LIMIT 1");
            ps.setInt(1, idPerangkat);
            rs = ps.executeQuery();
            if (rs.next()) {
                DataSensorEnt d = new DataSensorEnt();
                d.setIdData(rs.getLong("id_data"));
                d.setIdPerangkat(rs.getInt("id_perangkat"));
                d.setAccelX(rs.getDouble("accel_x"));
                d.setAccelY(rs.getDouble("accel_y"));
                d.setAccelZ(rs.getDouble("accel_z"));
                d.setGyroX(rs.getDouble("gyro_x"));
                d.setGyroY(rs.getDouble("gyro_y"));
                d.setGyroZ(rs.getDouble("gyro_z"));
                d.setSvm(rs.getDouble("svm"));
                d.setPitch(rs.getDouble("pitch"));
                d.setRoll(rs.getDouble("roll"));
                d.setHeartRate(rs.getInt("heart_rate"));
                d.setSpo2(rs.getInt("spo2"));
                d.setLatitude(rs.getDouble("latitude"));
                d.setLongitude(rs.getDouble("longitude"));
                d.setWaktuBaca(rs.getString("waktu_baca"));
                return d;
            }
            return null;
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: getSensorHistory(idPerangkat)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Mengambil 50 sampel telemetri terakhir dari 'tb_data_sensor'.
    // Data ini dikonsumsi oleh chart/grafik di aplikasi Android untuk visualisasi
    // tren detak jantung (BPM), saturasi oksigen (SpO2), dan percepatan (SVM).
    public List<DataSensorEnt> getSensorHistory(int idPerangkat) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        List<DataSensorEnt> list = new ArrayList<>();
        try {
            conn = getConnection();
            ps = conn.prepareStatement("SELECT * FROM tb_data_sensor WHERE id_perangkat = ? ORDER BY id_data DESC LIMIT 50");
            ps.setInt(1, idPerangkat);
            rs = ps.executeQuery();
            while (rs.next()) {
                DataSensorEnt d = new DataSensorEnt();
                d.setIdData(rs.getLong("id_data"));
                d.setIdPerangkat(rs.getInt("id_perangkat"));
                d.setAccelX(rs.getDouble("accel_x"));
                d.setAccelY(rs.getDouble("accel_y"));
                d.setAccelZ(rs.getDouble("accel_z"));
                d.setSvm(rs.getDouble("svm"));
                d.setHeartRate(rs.getInt("heart_rate"));
                d.setSpo2(rs.getInt("spo2"));
                d.setWaktuBaca(rs.getString("waktu_baca"));
                list.add(d);
            }
            return list;
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: getAllKejadian()
    // ------------------------------------------------------------------------
    // Fungsi:
    // Mengambil seluruh riwayat insiden darurat dari 'tb_kejadian_darurat'
    // beserta relasi nama lansia, nomor kamar, dan nama perangkat sabuk.
    // Diurutkan dari insiden paling baru ke lama.
    public List<KejadianDaruratEnt> getAllKejadian() throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        List<KejadianDaruratEnt> list = new ArrayList<>();
        try {
            conn = getConnection();
            String sql = "SELECT k.*, p.nama_lengkap AS nama_penghuni, p.alamat_kamar, dev.nama_perangkat, dev.mac_address " +
                    "FROM tb_kejadian_darurat k " +
                    "LEFT JOIN tb_penghuni p ON k.id_penghuni = p.id_penghuni " +
                    "LEFT JOIN tb_perangkat dev ON k.id_perangkat = dev.id_perangkat " +
                    "ORDER BY k.id_kejadian DESC";
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapKejadianWithRelations(rs));
            }
            return list;
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: getKejadianById(idKejadian)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Mengambil rincian lengkap satu insiden darurat spesifik (termasuk nilai SVM,
    // sudut jatuh roll/pitch, koordinat GPS, dan status respon perawat).
    public KejadianDaruratEnt getKejadianById(int idKejadian) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            String sql = "SELECT k.*, p.nama_lengkap AS nama_penghuni, p.alamat_kamar, dev.nama_perangkat, dev.mac_address " +
                    "FROM tb_kejadian_darurat k " +
                    "LEFT JOIN tb_penghuni p ON k.id_penghuni = p.id_penghuni " +
                    "LEFT JOIN tb_perangkat dev ON k.id_perangkat = dev.id_perangkat " +
                    "WHERE k.id_kejadian = ?";
            ps = conn.prepareStatement(sql);
            ps.setInt(1, idKejadian);
            rs = ps.executeQuery();
            if (rs.next()) {
                return mapKejadianWithRelations(rs);
            }
            return null;
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: respondKejadian(idKejadian, catatan, status)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Digunakan oleh Caregiver/Perawat di aplikasi Android untuk menandai bahwa
    // insiden darurat telah ditangani (status: SELESAI / DALAM_PENANGANAN / FALSE_ALARM)
    // dan menambahkan catatan medis/tindakan pertolongan pertama yang diberikan.
    public boolean respondKejadian(int idKejadian, String catatan, String status) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = getConnection();
            String st = (status != null && !status.isEmpty()) ? status : "SELESAI";
            String sql = "UPDATE tb_kejadian_darurat SET status_kejadian = ?, catatan = ?, waktu_respon = NOW() WHERE id_kejadian = ?";
            ps = conn.prepareStatement(sql);
            ps.setString(1, st);
            ps.setString(2, catatan);
            ps.setInt(3, idKejadian);
            return ps.executeUpdate() > 0;
        } finally {
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ========================================================================
    // 4. MODUL PENGATURAN SISTEM & THRESHOLD SENSOR (tb_pengaturan)
    // ========================================================================

    // ------------------------------------------------------------------------
    // METHOD: getPengaturan()
    // ------------------------------------------------------------------------
    // Fungsi:
    // Mengambil konfigurasi parameter sistem global (default sabuk 1):
    // - threshold_svm, threshold_tilt, durasi_countdown, status buzzer,
    // - batas minimum detak jantung BPM & saturasi oksigen SpO2,
    // - koordinat titik pusat wisma panti dan radius geofencing aman (meter).
    public PengaturanEnt getPengaturan() throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("SELECT * FROM tb_pengaturan ORDER BY id_pengaturan ASC LIMIT 1");
            rs = ps.executeQuery();
            if (rs.next()) {
                PengaturanEnt cfg = new PengaturanEnt();
                cfg.setIdPengaturan(rs.getInt("id_pengaturan"));
                cfg.setThresholdSvm(rs.getDouble("threshold_svm"));
                cfg.setThresholdTilt(rs.getDouble("threshold_tilt"));
                try {
                    cfg.setDurasiMiringDetik(rs.getInt("durasi_miring_detik"));
                } catch (Exception e) { cfg.setDurasiMiringDetik(5); }
                cfg.setThresholdRollCount(rs.getInt("threshold_roll_count"));
                cfg.setDurasiCountdown(rs.getInt("durasi_countdown"));
                cfg.setBuzzerAktif(rs.getInt("buzzer_aktif") == 1);
                try {
                    cfg.setMinHeartRate(rs.getInt("min_heart_rate"));
                } catch (Exception e) { cfg.setMinHeartRate(60); }
                try {
                    cfg.setMinSpo2(rs.getInt("min_spo2"));
                } catch (Exception e) { cfg.setMinSpo2(95); }
                try {
                    cfg.setLokasiDefaultNama(rs.getString("lokasi_default_nama"));
                } catch (Exception e) { cfg.setLokasiDefaultNama("Wisma Panti Lansia"); }
                try {
                    cfg.setDefaultLatitude(rs.getDouble("default_latitude"));
                } catch (Exception e) { cfg.setDefaultLatitude(-3.6954360); }
                try {
                    cfg.setDefaultLongitude(rs.getDouble("default_longitude"));
                } catch (Exception e) { cfg.setDefaultLongitude(128.1814270); }
                try {
                    cfg.setMaxDistanceMeters(rs.getInt("max_distance_meters"));
                } catch (Exception e) { cfg.setMaxDistanceMeters(100); }
                try {
                    cfg.setTerakhirDiperbarui(rs.getString("updated_at"));
                } catch (Exception ignored) {}
                return cfg;
            }
            // Default fallback jika tabel pengaturan masih kosong
            PengaturanEnt def = new PengaturanEnt();
            def.setThresholdSvm(2.70);
            def.setThresholdTilt(55.0);
            def.setDurasiMiringDetik(5);
            def.setThresholdRollCount(5);
            def.setDurasiCountdown(30);
            def.setBuzzerAktif(true);
            def.setMinHeartRate(60);
            def.setMinSpo2(95);
            def.setLokasiDefaultNama("Wisma Panti Lansia");
            def.setDefaultLatitude(-3.6954360);
            def.setDefaultLongitude(128.1814270);
            def.setMaxDistanceMeters(100);
            return def;
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: updatePengaturan(cfg)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Shortcut untuk memperbarui konfigurasi pada perangkat utama (ID = 1).
    public boolean updatePengaturan(PengaturanEnt cfg) throws SQLException {
        return updatePengaturanByPerangkat(1, cfg);
    }

    // ------------------------------------------------------------------------
    // METHOD: getPerangkatByPenghuni(idPenghuni)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Mengambil data sabuk pintar yang sedang dikenakan oleh lansia tertentu.
    public PerangkatEnt getPerangkatByPenghuni(int idPenghuni) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("SELECT * FROM tb_perangkat WHERE id_penghuni = ? LIMIT 1");
            ps.setInt(1, idPenghuni);
            rs = ps.executeQuery();
            if (rs.next()) {
                return mapPerangkat(rs);
            }
            return null;
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ------------------------------------------------------------------------
    // METHOD: getPengaturanByPerangkat(idPerangkat)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Mengambil konfigurasi ambang batas spesifik untuk satu sabuk IoT berdasarkan id_perangkat.
    // Parameter ini disinkronkan saat Raspberry Pi Pico W melakukan GET /api/iot/config.
    public PengaturanEnt getPengaturanByPerangkat(int idPerangkat) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            ps = conn.prepareStatement("SELECT * FROM tb_pengaturan WHERE id_perangkat = ? LIMIT 1");
            ps.setInt(1, idPerangkat);
            rs = ps.executeQuery();
            if (rs.next()) {
                PengaturanEnt cfg = new PengaturanEnt();
                cfg.setIdPengaturan(rs.getInt("id_pengaturan"));
                cfg.setThresholdSvm(rs.getDouble("threshold_svm"));
                cfg.setThresholdTilt(rs.getDouble("threshold_tilt"));
                try { cfg.setDurasiMiringDetik(rs.getInt("durasi_miring_detik")); } catch (Exception e) { cfg.setDurasiMiringDetik(5); }
                cfg.setThresholdRollCount(rs.getInt("threshold_roll_count"));
                cfg.setDurasiCountdown(rs.getInt("durasi_countdown"));
                cfg.setBuzzerAktif(rs.getInt("buzzer_aktif") == 1);
                try { cfg.setMinHeartRate(rs.getInt("min_heart_rate")); } catch (Exception e) { cfg.setMinHeartRate(60); }
                try { cfg.setMinSpo2(rs.getInt("min_spo2")); } catch (Exception e) { cfg.setMinSpo2(95); }
                try { cfg.setLokasiDefaultNama(rs.getString("lokasi_default_nama")); } catch (Exception e) { cfg.setLokasiDefaultNama("Wisma Panti Lansia"); }
                try { cfg.setDefaultLatitude(rs.getDouble("default_latitude")); } catch (Exception e) { cfg.setDefaultLatitude(-3.6954360); }
                try { cfg.setDefaultLongitude(rs.getDouble("default_longitude")); } catch (Exception e) { cfg.setDefaultLongitude(128.1814270); }
                try { cfg.setMaxDistanceMeters(rs.getInt("max_distance_meters")); } catch (Exception e) { cfg.setMaxDistanceMeters(100); }
                try { cfg.setTerakhirDiperbarui(rs.getString("updated_at")); } catch (Exception ignored) {}
                return cfg;
            }
            return getPengaturan(); // fallback
        } finally {
            closeResource(rs);
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ========================================================================
    // 3. MODUL KONFIGURASI DINAMIS & AMBANG BATAS (tb_pengaturan)
    // ========================================================================

    // ------------------------------------------------------------------------
    // METHOD: updatePengaturanByPerangkat(idPerangkat, cfg)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Memperbarui parameter sensitivitas algoritma jatuh dan ambang batas kesehatan
    // yang akan disinkronkan ke Raspberry Pi Pico W:
    // - threshold_svm        : Ambang batas lonjakan percepatan jatuh (default: 2.70g)
    // - threshold_tilt       : Ambang batas kemiringan tubuh (default: 55.0 derajat)
    // - durasi_miring_detik  : Durasi miring terus-menerus sebelum alarm (default: 5 detik)
    // - durasi_countdown     : Lama waktu buzzer berbunyi sebelum kirim darurat (default: 30 detik)
    // - min_heart_rate       : Batas detak jantung terendah BPM (default: 60)
    // - min_spo2             : Batas saturasi oksigen darah terendah % (default: 95)
    // - max_distance_meters  : Radius geofence aman dari titik wisma (default: 100 meter)
    public boolean updatePengaturanByPerangkat(int idPerangkat, PengaturanEnt cfg) throws SQLException {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = getConnection();
            String sql = "UPDATE tb_pengaturan SET threshold_svm = ?, threshold_tilt = ?, durasi_miring_detik = ?, threshold_roll_count = ?, durasi_countdown = ?, buzzer_aktif = ?, " +
                    "min_heart_rate = ?, min_spo2 = ?, lokasi_default_nama = ?, default_latitude = ?, default_longitude = ?, max_distance_meters = ?, " +
                    "updated_at = NOW() WHERE id_perangkat = ?";
            ps = conn.prepareStatement(sql);
            ps.setDouble(1, cfg.getThresholdSvm() != null ? cfg.getThresholdSvm() : 2.70);
            ps.setDouble(2, cfg.getThresholdTilt() != null ? cfg.getThresholdTilt() : 55.0);
            ps.setInt(3, cfg.getDurasiMiringDetik() != null ? cfg.getDurasiMiringDetik() : 5);
            ps.setInt(4, cfg.getThresholdRollCount() != null ? cfg.getThresholdRollCount() : 5);
            ps.setInt(5, cfg.getDurasiCountdown() != null ? cfg.getDurasiCountdown() : 30);
            ps.setInt(6, (cfg.getBuzzerAktif() != null && cfg.getBuzzerAktif()) ? 1 : 0);
            ps.setInt(7, cfg.getMinHeartRate() != null ? cfg.getMinHeartRate() : 60);
            ps.setInt(8, cfg.getMinSpo2() != null ? cfg.getMinSpo2() : 95);
            ps.setString(9, cfg.getLokasiDefaultNama() != null ? cfg.getLokasiDefaultNama() : "Wisma Panti Lansia");
            ps.setDouble(10, cfg.getDefaultLatitude() != null ? cfg.getDefaultLatitude() : -3.6954360);
            ps.setDouble(11, cfg.getDefaultLongitude() != null ? cfg.getDefaultLongitude() : 128.1814270);
            ps.setInt(12, cfg.getMaxDistanceMeters() != null ? cfg.getMaxDistanceMeters() : 100);
            ps.setInt(13, idPerangkat);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                closeResource(ps);
                String insSql = "INSERT INTO tb_pengaturan (id_perangkat, threshold_svm, threshold_tilt, durasi_miring_detik, threshold_roll_count, durasi_countdown, buzzer_aktif, " +
                        "min_heart_rate, min_spo2, lokasi_default_nama, default_latitude, default_longitude, max_distance_meters) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                ps = conn.prepareStatement(insSql);
                ps.setInt(1, idPerangkat);
                ps.setDouble(2, cfg.getThresholdSvm() != null ? cfg.getThresholdSvm() : 2.70);
                ps.setDouble(3, cfg.getThresholdTilt() != null ? cfg.getThresholdTilt() : 55.0);
                ps.setInt(4, cfg.getDurasiMiringDetik() != null ? cfg.getDurasiMiringDetik() : 5);
                ps.setInt(5, cfg.getThresholdRollCount() != null ? cfg.getThresholdRollCount() : 5);
                ps.setInt(6, cfg.getDurasiCountdown() != null ? cfg.getDurasiCountdown() : 30);
                ps.setInt(7, (cfg.getBuzzerAktif() != null && cfg.getBuzzerAktif()) ? 1 : 0);
                ps.setInt(8, cfg.getMinHeartRate() != null ? cfg.getMinHeartRate() : 60);
                ps.setInt(9, cfg.getMinSpo2() != null ? cfg.getMinSpo2() : 95);
                ps.setString(10, cfg.getLokasiDefaultNama() != null ? cfg.getLokasiDefaultNama() : "Wisma Panti Lansia");
                ps.setDouble(11, cfg.getDefaultLatitude() != null ? cfg.getDefaultLatitude() : -3.6954360);
                ps.setDouble(12, cfg.getDefaultLongitude() != null ? cfg.getDefaultLongitude() : 128.1814270);
                ps.setInt(13, cfg.getMaxDistanceMeters() != null ? cfg.getMaxDistanceMeters() : 100);
                rows = ps.executeUpdate();
            }
            return rows > 0;
        } finally {
            closeResource(ps);
            closeResource(conn);
        }
    }

    // ==========================================
    // ROW MAPPERS (HELPER PRIVATE METHODS)
    // ==========================================

    private UserEnt mapUser(ResultSet rs) throws SQLException {
        UserEnt u = new UserEnt();
        u.setIdUser(rs.getInt("id_user"));
        u.setNamaLengkap(rs.getString("nama_lengkap"));
        u.setEmail(rs.getString("email"));
        u.setUsername(rs.getString("username"));
        u.setRole(rs.getString("role"));
        u.setNoTelepon(rs.getString("no_telepon"));
        u.setFotoProfil(rs.getString("foto_profil"));
        u.setEmailVerified(rs.getInt("email_verified") == 1);
        u.setStatusAktif(rs.getInt("status_aktif") == 1);
        u.setTelegramChatId(rs.getString("telegram_chat_id"));
        return u;
    }

    private PenghuniEnt mapPenghuni(ResultSet rs) throws SQLException {
        PenghuniEnt p = new PenghuniEnt();
        p.setIdPenghuni(rs.getInt("id_penghuni"));
        p.setNamaLengkap(rs.getString("nama_lengkap"));
        p.setTanggalLahir(rs.getString("tanggal_lahir"));
        p.setJenisKelamin(rs.getString("jenis_kelamin"));
        p.setAlamatKamar(rs.getString("alamat_kamar"));
        p.setNoTelepon(rs.getString("no_telepon"));
        p.setRiwayatPenyakit(rs.getString("riwayat_penyakit"));
        p.setFotoProfil(rs.getString("foto_profil"));
        p.setStatusAktif(rs.getInt("status_aktif") == 1);
        try {
            int idU = rs.getInt("id_user");
            if (!rs.wasNull()) p.setIdUser(idU);
        } catch (SQLException ignored) {}
        try {
            int idUl = rs.getInt("id_user_lansia");
            if (!rs.wasNull()) p.setIdUserLansia(idUl);
        } catch (SQLException ignored) {}
        try {
            int idDev = rs.getInt("id_perangkat");
            if (!rs.wasNull()) p.setIdPerangkat(idDev);
        } catch (SQLException ignored) {}
        try {
            p.setNamaPerangkat(rs.getString("nama_perangkat"));
        } catch (SQLException ignored) {}
        try {
            p.setMacAddress(rs.getString("mac_address"));
        } catch (SQLException ignored) {}
        return p;
    }

    private PerangkatEnt mapPerangkat(ResultSet rs) throws SQLException {
        PerangkatEnt dev = new PerangkatEnt();
        dev.setIdPerangkat(rs.getInt("id_perangkat"));
        dev.setNamaPerangkat(rs.getString("nama_perangkat"));
        dev.setMacAddress(rs.getString("mac_address"));
        dev.setIdPenghuni(rs.getInt("id_penghuni"));
        dev.setIpAddress(rs.getString("ip_address"));
        dev.setVersiFirmware(rs.getString("versi_firmware"));
        dev.setStatusBaterai(rs.getInt("status_baterai"));
        dev.setStatusKoneksi(rs.getInt("status_koneksi") == 1);
        dev.setTerakhirTerhubung(rs.getString("terakhir_terhubung"));
        return dev;
    }

    private KejadianDaruratEnt mapKejadianWithRelations(ResultSet rs) throws SQLException {
        KejadianDaruratEnt k = new KejadianDaruratEnt();
        k.setIdKejadian(rs.getInt("id_kejadian"));
        k.setJenisKejadian(rs.getString("jenis_kejadian"));
        k.setSvmValue(rs.getDouble("svm_value"));
        k.setPitchValue(rs.getDouble("pitch_value"));
        k.setRollValue(rs.getDouble("roll_value"));
        k.setAccelX(rs.getDouble("accel_x"));
        k.setAccelY(rs.getDouble("accel_y"));
        k.setAccelZ(rs.getDouble("accel_z"));
        k.setLatitude(rs.getDouble("latitude"));
        k.setLongitude(rs.getDouble("longitude"));
        k.setAlamatLokasi(rs.getString("alamat_lokasi"));
        k.setStatusKejadian(rs.getString("status_kejadian"));
        k.setWaktuKejadian(rs.getString("waktu_kejadian"));
        k.setWaktuRespon(rs.getString("waktu_respon"));
        k.setCatatan(rs.getString("catatan"));

        PenghuniEnt pen = new PenghuniEnt();
        pen.setIdPenghuni(rs.getInt("id_penghuni"));
        pen.setNamaLengkap(rs.getString("nama_penghuni"));
        pen.setAlamatKamar(rs.getString("alamat_kamar"));
        k.setPenghuni(pen);

        PerangkatEnt dev = new PerangkatEnt();
        dev.setIdPerangkat(rs.getInt("id_perangkat"));
        dev.setNamaPerangkat(rs.getString("nama_perangkat"));
        dev.setMacAddress(rs.getString("mac_address"));
        k.setPerangkat(dev);

        return k;
    }
}
