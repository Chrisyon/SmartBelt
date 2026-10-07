package com.smartbelt.caregiver.data.api

//deklasrasi restful api backend
//Mencatat daftar alamat server (seperti alamat Login, Data Lansia, Data Jatuh).
//Menentukan jenis aksi (apakah mau GET mengambil data, atau POST mengirim data).

import com.smartbelt.caregiver.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // --- AUTH ---
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<User>>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<AuthResponse>>

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<ApiResponse<String>>

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<ApiResponse<String>>

    @POST("api/auth/verify-token")
    suspend fun verifyResetToken(@Body body: Map<String, String>): Response<ApiResponse<Boolean>>

    @GET("api/auth/profile")
    suspend fun getProfile(): Response<ApiResponse<User>>

    // --- USER MANAGEMENT (ADMIN) ---
    @GET("api/user")
    suspend fun getAllUsers(@Query("role") role: String? = null): Response<ApiResponse<List<User>>>

    @GET("api/user/{id}")
    suspend fun getUserById(@Path("id") id: Int): Response<ApiResponse<User>>

    @PUT("api/user/{id}")
    suspend fun updateUser(@Path("id") id: Int, @Body user: User): Response<ApiResponse<User>>

    @POST("api/user")
    suspend fun createUser(@Body request: CreateUserRequest): Response<ApiResponse<User>>

    @DELETE("api/user/{id}")
    suspend fun deactivateUser(@Path("id") id: Int): Response<ApiResponse<String>>

    @PUT("api/user/{id}/approve")
    suspend fun approveUser(@Path("id") id: Int): Response<ApiResponse<User>>

    // --- PENGHUNI ---
    @GET("api/penghuni")
    suspend fun getAllPenghuni(): Response<ApiResponse<List<Penghuni>>>

    @GET("api/penghuni/keluarga/{userId}")
    suspend fun getPenghuniByKeluarga(@Path("userId") userId: Int): Response<ApiResponse<List<Penghuni>>>

    @GET("api/penghuni/lansia/{userId}")
    suspend fun getPenghuniByLansia(@Path("userId") userId: Int): Response<ApiResponse<Penghuni>>

    @GET("api/penghuni/{id}")
    suspend fun getPenghuniById(@Path("id") id: Int): Response<ApiResponse<Penghuni>>

    @POST("api/penghuni")
    suspend fun createPenghuni(@Body penghuni: Penghuni): Response<ApiResponse<Penghuni>>

    @PUT("api/penghuni/{id}")
    suspend fun updatePenghuni(@Path("id") id: Int, @Body penghuni: Penghuni): Response<ApiResponse<Penghuni>>

    @DELETE("api/penghuni/{id}")
    suspend fun deletePenghuni(@Path("id") id: Int): Response<ApiResponse<String>>

    @GET("api/user/{id}/keluarga")
    suspend fun getUserKeluarga(@Path("id") id: Int): Response<ApiResponse<KeluargaRelation>>

    @PUT("api/user/{id}/keluarga")
    suspend fun updateUserKeluarga(
        @Path("id") id: Int,
        @Body body: Map<String, Any?>
    ): Response<ApiResponse<KeluargaRelation>>

    @GET("api/penghuni/{id}/kontak")
    suspend fun getKontakByPenghuni(@Path("id") id: Int): Response<ApiResponse<List<KontakDarurat>>>

    @POST("api/penghuni/{id}/kontak")
    suspend fun addKontak(@Path("id") id: Int, @Body kontak: KontakDarurat): Response<ApiResponse<KontakDarurat>>

    @PUT("api/penghuni/kontak/{idKontak}")
    suspend fun updateKontak(@Path("idKontak") idKontak: Int, @Body kontak: KontakDarurat): Response<ApiResponse<KontakDarurat>>

    @DELETE("api/penghuni/kontak/{idKontak}")
    suspend fun deleteKontak(@Path("idKontak") idKontak: Int): Response<ApiResponse<String>>

    // --- KEJADIAN DARURAT ---
    @GET("api/kejadian")
    suspend fun getAllKejadian(): Response<ApiResponse<List<KejadianDarurat>>>

    @GET("api/iot/active-emergencies")
    suspend fun getActiveEmergencies(): Response<ApiResponse<List<KejadianDarurat>>>

    @GET("api/kejadian/{id}")
    suspend fun getKejadianById(@Path("id") id: Int): Response<ApiResponse<KejadianDarurat>>

    @PUT("api/kejadian/{id}/respon")
    suspend fun respondToKejadian(
        @Path("id") id: Int,
        @Body body: Map<String, String>
    ): Response<ApiResponse<KejadianDarurat>>

    // --- PERANGKAT & SENSOR ---
    @GET("api/perangkat")
    suspend fun getAllPerangkat(): Response<ApiResponse<List<Perangkat>>>

    @GET("api/iot/sensor-latest/{id}")
    suspend fun getLatestSensorData(@Path("id") id: Int): Response<ApiResponse<DataSensor>>

    @GET("api/perangkat/{id}/sensor-history")
    suspend fun getSensorHistory(@Path("id") id: Int): Response<ApiResponse<List<DataSensor>>>

    // --- IOT CANCEL / SOS ---
    @POST("api/iot/emergency")
    suspend fun triggerEmergency(@Body request: EmergencyRequest): Response<ApiResponse<KejadianDarurat>>

    @POST("api/iot/cancel")
    suspend fun cancelEmergency(@Query("macAddress") macAddress: String): Response<ApiResponse<String>>

    @GET("api/iot/status/{macAddress}")
    suspend fun isEmergencyActive(@Path("macAddress") macAddress: String): Response<ApiResponse<Boolean>>

    // --- PENGATURAN (SENSITIVITAS, ALARM, HITUNG MUNDUR) ---
    @GET("api/pengaturan/perangkat/{idPerangkat}")
    suspend fun getPengaturanByPerangkat(@Path("idPerangkat") idPerangkat: Int): Response<ApiResponse<Pengaturan>>

    @GET("api/pengaturan/user/{idUser}")
    suspend fun getPengaturanByUser(@Path("idUser") idUser: Int): Response<ApiResponse<Pengaturan>>

    @PUT("api/pengaturan/user/{idUser}")
    suspend fun updatePengaturanByUser(
        @Path("idUser") idUser: Int,
        @Body req: UpdatePengaturanRequest
    ): Response<ApiResponse<Pengaturan>>

    // --- LOKASI GPS SMART-BELT (ADMIN & KELUARGA) ---
    @GET("api/perangkat/lokasi-aktif")
    suspend fun getBeltLocations(
        @Query("keluargaId") keluargaId: Int? = null
    ): Response<ApiResponse<List<BeltLocation>>>
}



