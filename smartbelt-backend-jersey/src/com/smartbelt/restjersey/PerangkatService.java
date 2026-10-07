package com.smartbelt.restjersey;

import com.smartbelt.db.DbHelper;
import com.smartbelt.obj.BeltLocationEnt;
import com.smartbelt.obj.DataSensorEnt;
import com.smartbelt.obj.PerangkatEnt;
import com.smartbelt.obj.ResponseEnt;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

// ============================================================================
// SERVICE MANAJEMEN PERANGKAT SMART-BELT (REST API JERSEY)
// ============================================================================
// Base Path: /api/perangkat
// File ini bertugas menyediakan endpoint data perangkat keras IoT Smart-Belt:
// 1. Daftar seluruh perangkat sabuk yang terdaftar di sistem (tb_perangkat).
// 2. Monitoring lokasi terkini (Live GPS Tracker) untuk ditampilkan di peta Android.
// 3. Riwayat log pembacaan sensor (BPM, SpO2, Accelerometer) untuk grafik riwayat kesehatan.
@Path("/perangkat")
public class PerangkatService {

    // ------------------------------------------------------------------------
    // 1. GET SEMUA PERANGKAT (GET /api/perangkat)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Mengambil seluruh data sabuk IoT yang ada di panti (nama perangkat, MAC address,
    // nomor seri, status koneksi ONLINE/OFFLINE, dan baterai terakhir).
    // Endpoint ini dipanggil oleh Admin atau Caregiver pada menu daftar sabuk.
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllPerangkat() {
        try {
            List<PerangkatEnt> list = DbHelper.getInstance().getAllPerangkat();
            return Response.ok(ResponseEnt.ok("Daftar perangkat", list)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }

    // ------------------------------------------------------------------------
    // 2. GET LOKASI AKTIF SELURUH SABUK (GET /api/perangkat/lokasi-aktif)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Mengambil koordinat GPS (latitude, longitude) terkini, nama lansia pemakai,
    // dan status keamanan (di dalam geofencing panti atau di luar zona aman).
    // Digunakan oleh antarmuka Peta GPS di aplikasi Android untuk pelacakan real-time.
    @GET
    @Path("/lokasi-aktif")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLokasiAktif() {
        try {
            List<BeltLocationEnt> list = DbHelper.getInstance().getLokasiAktif();
            return Response.ok(ResponseEnt.ok("Lokasi aktif sabuk", list)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/locations")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLocations() {
        return getLokasiAktif();
    }

    // ------------------------------------------------------------------------
    // 3. GET RIWAYAT SENSOR PERANGKAT (GET /api/perangkat/{id}/sensor-history)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Mengambil 50-100 data pembacaan telemetri terakhir dari perangkat dengan ID tertentu.
    // Data mencakup: Heart Rate (BPM), SpO2 (%), Sudut Kemiringan (Tilt Pitch/Roll),
    // dan Akselerasi (G-Force).
    // Digunakan oleh aplikasi Android untuk merender grafik garis dinamika vital sign.
    // Parameter: {id} = ID perangkat (tb_perangkat.id_perangkat). Jika kosong, default ke 1.
    @GET
    @Path("/{id}/sensor-history")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSensorHistory(@PathParam("id") Integer id) {
        try {
            int devId = (id != null) ? id : 1;
            List<DataSensorEnt> list = DbHelper.getInstance().getSensorHistory(devId);
            return Response.ok(ResponseEnt.ok("Riwayat sensor", list)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }
}
