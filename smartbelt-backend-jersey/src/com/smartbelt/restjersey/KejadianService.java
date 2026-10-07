package com.smartbelt.restjersey;

import com.smartbelt.db.DbHelper;
import com.smartbelt.obj.KejadianDaruratEnt;
import com.smartbelt.obj.ResponseEnt;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

// ============================================================================
// SERVICE KEJADIAN DARURAT (REST API JERSEY) - LOG RIWAYAT & TINDAKAN TANGGAP
// ============================================================================
// Base Path: /api/kejadian
// Kelas ini bertanggung jawab atas pengelolaan insiden darurat lansia:
// 1. Menampilkan seluruh daftar riwayat kejadian darurat (Incident History)
// 2. Mengambil rincian parameter sensor (SVM max, tilt, GPS) dari satu insiden
// 3. Mencatat tindakan respons dan pembaruan status (SELESAI / DIBATALKAN)
//    oleh perawat atau caregiver.
// ============================================================================
@Path("/kejadian")
public class KejadianService {

    // ------------------------------------------------------------------------
    // 1. ENDPOINT DAFTAR SEMUA KEJADIAN (GET /api/kejadian)
    // ------------------------------------------------------------------------
    // Dipanggil oleh: Menu "Riwayat Insiden" (IncidentHistoryActivity) di Android.
    // Menghasilkan daftar kejadian diurutkan dari yang paling baru (DESC).
    // Menampilkan jenis kejadian (JATUH, PANIC_BUTTON, TILT_EKSTREM, KELUAR_ZONA_AMAN),
    // waktu kejadian, lokasi koordinat, serta status tanggap perawat.
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllKejadian() {
        try {
            List<KejadianDaruratEnt> list = DbHelper.getInstance().getAllKejadian();
            return Response.ok(ResponseEnt.ok("Riwayat insiden darurat berhasil dimuat", list)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error("Gagal memuat riwayat insiden: " + e.getMessage()))
                    .build();
        }
    }

    // ------------------------------------------------------------------------
    // 2. ENDPOINT DETAIL KEJADIAN BERDASARKAN ID (GET /api/kejadian/{id})
    // ------------------------------------------------------------------------
    // Mengambil data lengkap sebuah insiden darurat tertentu berdasarkan ID kejadian.
    // Menyajikan data nilai puncak akselerasi (SVM max), sudut tilt pitch/roll,
    // titik koordinat peta GPS Google Maps, nama lansia, dan nomor sabuk.
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getKejadianById(@PathParam("id") Integer id) {
        try {
            KejadianDaruratEnt k = DbHelper.getInstance().getKejadianById(id);
            if (k != null) {
                return Response.ok(ResponseEnt.ok("Detail insiden berhasil dimuat", k)).build();
            }
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ResponseEnt.error("Insiden dengan ID tersebut tidak ditemukan!"))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }

    // ------------------------------------------------------------------------
    // 3. ENDPOINT RESPONS TINDAKAN PERAWAT (PUT /api/kejadian/{id}/respon)
    // ------------------------------------------------------------------------
    // Dipanggil saat perawat/caregiver telah menolong lansia dan mengisi catatan
    // penanganan di aplikasi Android (misal: "Lansia dibantu berdiri, tensi normal").
    // Alur Kerja:
    // 1. Membaca catatan penanganan dan status baru (misal: 'SELESAI' atau 'DITANGANI').
    // 2. Menyimpan waktu respon saat ini (waktu_respon = NOW()) di 'tb_kejadian_darurat'.
    // 3. Memperbarui kolom status_kejadian dan catatan medis perawat di database.
    @PUT
    @Path("/{id}/respon")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response respondKejadian(@PathParam("id") Integer id, Map<String, String> body) {
        try {
            String catatan = body.get("catatan");
            String status = body.get("statusKejadian");
            if (status == null) status = body.get("status");
            if (status == null || status.trim().isEmpty()) {
                status = "SELESAI";
            }

            boolean ok = DbHelper.getInstance().respondKejadian(id, catatan, status);
            if (ok) {
                KejadianDaruratEnt updated = DbHelper.getInstance().getKejadianById(id);
                return Response.ok(ResponseEnt.ok("Respon insiden berhasil dicatat", updated)).build();
            }
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ResponseEnt.error("Insiden darurat tidak ditemukan untuk diperbarui"))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error("Gagal mencatat respon insiden: " + e.getMessage()))
                    .build();
        }
    }
}
