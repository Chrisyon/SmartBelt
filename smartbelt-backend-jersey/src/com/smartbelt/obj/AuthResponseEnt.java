package com.smartbelt.obj;

import java.io.Serializable;

// ============================================================================
// DTO RESPONSE LOGIN & OTENTIKASI (AuthResponseEnt)
// ============================================================================
// Objek data transfer ini dikembalikan saat pengguna berhasil login via POST /api/auth/login.
// Berisi token otorisasi Bearer untuk disimpan di SharedPreferences HP Android,
// serta rincian hak akses peran (Role: ADMIN, CAREGIVER, KELUARGA, PENGGUNA_BELT).
public class AuthResponseEnt implements Serializable {
    private static final long serialVersionUID = 1L;

    private String token;       // Token sesi akses ("JWT_...")
    private String tokenType;   // Tipe token ("Bearer")
    private Integer idUser;     // ID unik pengguna di tb_user
    private String username;    // Nama akun login
    private String email;       // Email terdaftar
    private String namaLengkap; // Nama asli pengguna
    private String role;        // Peran pengguna (ADMIN, CAREGIVER, KELUARGA, PENGGUNA_BELT)
    private String fotoProfil;  // URL/nama file foto profil avatar pengguna

    public AuthResponseEnt() {
    }

    public AuthResponseEnt(String token, String tokenType, Integer idUser, String username, String email, String namaLengkap, String role, String fotoProfil) {
        this.token = token;
        this.tokenType = tokenType;
        this.idUser = idUser;
        this.username = username;
        this.email = email;
        this.namaLengkap = namaLengkap;
        this.role = role;
        this.fotoProfil = fotoProfil;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public Integer getIdUser() {
        return idUser;
    }

    public void setIdUser(Integer idUser) {
        this.idUser = idUser;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNamaLengkap() {
        return namaLengkap;
    }

    public void setNamaLengkap(String namaLengkap) {
        this.namaLengkap = namaLengkap;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getFotoProfil() {
        return fotoProfil;
    }

    public void setFotoProfil(String fotoProfil) {
        this.fotoProfil = fotoProfil;
    }
}
