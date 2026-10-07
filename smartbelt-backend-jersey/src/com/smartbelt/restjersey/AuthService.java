package com.smartbelt.restjersey;

import com.smartbelt.db.DbHelper;
import com.smartbelt.obj.AuthResponseEnt;
import com.smartbelt.obj.ResponseEnt;
import com.smartbelt.obj.UserEnt;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.logging.Logger;

// ============================================================================
// SERVICE AUTENTIKASI (REST API JERSEY) - LOGIN, REGISTER & RESET PASSWORD
// ============================================================================
// Base Path: /api/auth
// File ini menangani seluruh siklus otentikasi dan otorisasi pengguna:
// 1. Login user dengan verifikasi BCrypt hash & cek status approval Admin
// 2. Registrasi mandiri akun baru (Admin, Caregiver, Keluarga, Lansia)
// 3. Permintaan dan verifikasi reset password via token/OTP email
// 4. Pengambilan profil user aktif
@Path("/auth")
public class AuthService {
    private static final Logger LOG = Logger.getLogger(AuthService.class.getName());

    // ------------------------------------------------------------------------
    // 1. ENDPOINT LOGIN (POST /api/auth/login)
    // ------------------------------------------------------------------------
    // Input JSON: { "usernameOrEmail": "...", "password": "..." }
    // Alur Kerja:
    // - Mencari user di 'tb_user' berdasarkan username atau email
    // - Mengecek kecocokan password menggunakan BCrypt.checkpw()
    // - Memeriksa status_aktif: jika bernilai 0, tolak login dengan HTTP 403 (FORBIDDEN)
    //   karena akun baru wajib di-approve terlebih dahulu oleh Admin Wisma
    // - Jika valid dan aktif, buat session token JWT di 'tb_session' (masa aktif 30 hari)
    // - Output: token Bearer, ID user, nama, email, role, foto profil
    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(Map<String, String> body) {
        try {
            String userOrEmail = body.get("usernameOrEmail");
            if (userOrEmail == null) userOrEmail = body.get("username");
            String pass = body.get("password");

            if (userOrEmail == null || pass == null || userOrEmail.trim().isEmpty() || pass.trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ResponseEnt.error("Username/Email dan Password wajib diisi!"))
                        .build();
            }

            AuthResponseEnt auth = DbHelper.getInstance().login(userOrEmail.trim(), pass.trim());
            if (auth != null) {
                return Response.ok(ResponseEnt.ok("Login berhasil", auth)).build();
            } else {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ResponseEnt.error("Username atau Password salah!"))
                        .build();
            }
        } catch (SecurityException se) {
            String msg = se.getMessage();
            if (msg != null && msg.contains("AKUN_MENUNGGU_APPROVAL: ")) {
                msg = msg.replace("AKUN_MENUNGGU_APPROVAL: ", "");
            }
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ResponseEnt.error(msg))
                    .build();
        } catch (Exception e) {
            LOG.severe("Error on login: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error("Gagal login: " + e.getMessage()))
                    .build();
        }
    }

    // ------------------------------------------------------------------------
    // 2. ENDPOINT REGISTRASI MANDIRI (POST /api/auth/register)
    // ------------------------------------------------------------------------
    // Input JSON: Objek UserEnt (nama, username, email, password, role, noTelepon, dll.)
    // Alur Kerja:
    // - Validasi field wajib (username, email, password)
    // - Menyetel status_aktif = false secara default (harus di-ACC Admin Wisma)
    // - Enkripsi password menggunakan BCrypt salt
    // - Simpan user ke 'tb_user'
    // - Jika role = PENGGUNA_BELT / LANSIA, otomatis buat profil di 'tb_penghuni'
    // - Jika role = KELUARGA, otomatis relasikan ke lansia pilihan di 'tb_keluarga_penghuni'
    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response register(UserEnt user) {
        try {
            if (user == null || user.getUsername() == null || user.getPassword() == null || user.getEmail() == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ResponseEnt.error("Data pendaftaran tidak lengkap!"))
                        .build();
            }

            // Pendaftaran mandiri akun baru selalu berstatus non-aktif (menunggu ACC Admin)
            user.setStatusAktif(false);

            UserEnt created = DbHelper.getInstance().register(user);
            if (created != null) {
                return Response.ok(ResponseEnt.ok("Pendaftaran berhasil! Akun Anda sedang menunggu persetujuan (approval) oleh Admin Wisma sebelum dapat digunakan untuk login.", created)).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ResponseEnt.error("Username atau Email sudah terdaftar!"))
                        .build();
            }
        } catch (Exception e) {
            LOG.severe("Error on register: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error("Gagal registrasi: " + e.getMessage()))
                    .build();
        }
    }

    // ------------------------------------------------------------------------
    // 3. ENDPOINT LUPA PASSWORD (POST /api/auth/forgot-password)
    // ------------------------------------------------------------------------
    // Input JSON: { "usernameOrEmail": "..." }
    // Alur Kerja:
    // - Cek keberadaan akun di database
    // - Generate token OTP acak 6 digit
    // - Simpan token ke 'tb_reset_password' dengan masa berlaku 15 menit
    // - Kirim email berisi token ke email user via EmailHelper (SMTP Gmail)
    @POST
    @Path("/forgot-password")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response forgotPassword(Map<String, String> body) {
        try {
            String userOrEmail = body.get("usernameOrEmail");
            if (userOrEmail == null) userOrEmail = body.get("username");
            if (userOrEmail == null) userOrEmail = body.get("email");

            if (userOrEmail == null || userOrEmail.trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ResponseEnt.error("Username atau email wajib diisi!"))
                        .build();
            }

            String token = DbHelper.getInstance().forgotPassword(userOrEmail.trim());
            if (token != null) {
                return Response.ok(ResponseEnt.ok("Kode verifikasi telah dikirim ke email!", token)).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ResponseEnt.error("Akun dengan username/email tersebut tidak ditemukan!"))
                        .build();
            }
        } catch (Exception e) {
            LOG.severe("Error on forgotPassword: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error("Gagal memproses lupa password: " + e.getMessage()))
                    .build();
        }
    }

    // ------------------------------------------------------------------------
    // 4. ENDPOINT RESET PASSWORD (POST /api/auth/reset-password)
    // ------------------------------------------------------------------------
    // Input JSON: { "token": "...", "newPassword": "..." }
    // Alur Kerja:
    // - Validasi token di 'tb_reset_password': periksa apakah token cocok, belum digunakan (used=0),
    //   dan belum kadaluwarsa (NOW() < expired_at)
    // - Jika valid: hash password baru dengan BCrypt dan update ke 'tb_user'
    // - Tandai token sebagai used=1 agar tidak bisa disalahgunakan lagi
    @POST
    @Path("/reset-password")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response resetPassword(Map<String, String> body) {
        try {
            String token = body.get("token");
            String newPass = body.get("newPassword");
            if (newPass == null) newPass = body.get("password");

            if (token == null || newPass == null || token.trim().isEmpty() || newPass.trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ResponseEnt.error("Token dan password baru wajib diisi!"))
                        .build();
            }

            boolean ok = DbHelper.getInstance().resetPassword(token.trim(), newPass.trim());
            if (ok) {
                return Response.ok(ResponseEnt.ok("Password berhasil diperbarui! Silakan login kembali.", "OK")).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ResponseEnt.error("Token tidak valid atau sudah kedaluwarsa!"))
                        .build();
            }
        } catch (Exception e) {
            LOG.severe("Error on resetPassword: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error("Gagal mereset password: " + e.getMessage()))
                    .build();
        }
    }

    // ------------------------------------------------------------------------
    // 5. ENDPOINT VERIFIKASI TOKEN (POST /api/auth/verify-token)
    // ------------------------------------------------------------------------
    // Mengecek apakah token reset password masih aktif sebelum user mengetik sandi baru.
    @POST
    @Path("/verify-token")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response verifyToken(Map<String, String> body) {
        try {
            String token = body.get("token");
            if (token == null || token.trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ResponseEnt.error("Token tidak boleh kosong!"))
                        .build();
            }

            boolean valid = DbHelper.getInstance().verifyResetToken(token.trim());
            return Response.ok(ResponseEnt.ok("Verifikasi token berhasil", valid)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error("Gagal verifikasi token: " + e.getMessage()))
                    .build();
        }
    }

    // ------------------------------------------------------------------------
    // 6. ENDPOINT AMBIL PROFIL USER (GET /api/auth/profile?id=...)
    // ------------------------------------------------------------------------
    // Mengembalikan informasi profil lengkap user (password di-null-kan demi keamanan).
    @GET
    @Path("/profile")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProfile(@QueryParam("id") Integer id) {
        try {
            int userId = (id != null && id > 0) ? id : 1;
            UserEnt u = DbHelper.getInstance().getUserById(userId);
            if (u != null) {
                u.setPassword(null);
                return Response.ok(ResponseEnt.ok("Profil berhasil dimuat", u)).build();
            }
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ResponseEnt.error("User tidak ditemukan"))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }
}
