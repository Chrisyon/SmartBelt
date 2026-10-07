package com.smartbelt.restjersey;

import com.google.gson.Gson;
import com.smartbelt.db.DbHelper;
import com.smartbelt.obj.*;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.logging.Logger;

// ============================================================================
// SERVICE IOT (REST API JERSEY) - KOMUNIKASI RASPBERRY PI PICO W & MOBILE APP
// ============================================================================
// Base Path: /api/iot
// File ini bertindak sebagai gerbang (gateway) HTTP API untuk:
// 1. Menerima trigger darurat (jatuh / tombol SOS) dari Raspberry Pi Pico W
// 2. Menerima telemetri data sensor periodik (MPU6050, MAX30102, GPS, Baterai)
// 3. Menyediakan konfigurasi dinamis (threshold SVM, countdown) ke Pico W
// 4. Polling status darurat dan pembatalan alarm dari sabuk / aplikasi Android
@Path("/iot")
public class IoTService {
    private static final Logger LOG = Logger.getLogger(IoTService.class.getName());
    private static final Gson gson = new Gson();

    // ------------------------------------------------------------------------
    // 1. ENDPOINT TRIGGER DARURAT (POST /api/iot/emergency)
    // ------------------------------------------------------------------------
    // Dipanggil oleh: Raspberry Pi Pico W saat mendeteksi jatuh (SVM tinggi)
    // atau tombol panic button (GP18) ditekan lama oleh lansia.
    // Alur Kerja:
    // - Menerima payload JSON: { macAddress, jenisKejadian, svmValue, pitch, roll, lat, lng, ... }
    // - Menyimpan record baru ke tabel 'tb_kejadian_darurat' dengan status 'COUNTDOWN' / 'AUTO_CALL'
    // - Mengirimkan notifikasi instan Telegram bot ke perawat secara asinkron
    // - Mengembalikan objek KejadianDaruratEnt ke pengirim
    @POST
    @Path("/emergency")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response triggerEmergency(String rawJson) {
        try {
            if (rawJson == null || rawJson.trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ResponseEnt.error("Body JSON tidak boleh kosong!"))
                        .build();
            }

            EmergencyRequestEnt req = gson.fromJson(rawJson, EmergencyRequestEnt.class);
            if (req == null || req.getMacAddress() == null || req.getMacAddress().trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ResponseEnt.error("MAC Address wajib disertakan!"))
                        .build();
            }

            LOG.info("🚨 Received Emergency Trigger from MAC: " + req.getMacAddress() + ", SVM: " + req.getSvmValue());
            KejadianDaruratEnt kejadian = DbHelper.getInstance().triggerEmergency(req);
            return Response.ok(ResponseEnt.ok("Sinyal darurat berhasil diproses", kejadian)).build();
        } catch (Exception e) {
            LOG.severe("Error on triggerEmergency: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error("Gagal memproses darurat: " + e.getMessage()))
                    .build();
        }
    }

    // ------------------------------------------------------------------------
    // 2. ENDPOINT TELEMETRI SENSOR PERIODIK (POST /api/iot/telemetry)
    // ------------------------------------------------------------------------
    // Dipanggil oleh: Raspberry Pi Pico W setiap interval tertentu (misal tiap 5-10 detik).
    // Alur Kerja:
    // - Menerima data realtime sensor: IMU (accel/gyro/svm/tilt), MAX30102 (BPM & SpO2), GPS & Baterai
    // - Menyimpan riwayat bacaan ke tabel 'tb_data_sensor'
    // - Memperbarui status koneksi (online=1), persentase baterai, dan 'terakhir_terhubung' di 'tb_perangkat'
    @POST
    @Path("/telemetry")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response receiveTelemetry(String rawJson) {
        try {
            if (rawJson == null || rawJson.trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ResponseEnt.error("Body JSON tidak boleh kosong!"))
                        .build();
            }

            TelemetryRequestEnt req = gson.fromJson(rawJson, TelemetryRequestEnt.class);
            if (req == null || req.getMacAddress() == null || req.getMacAddress().trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ResponseEnt.error("MAC Address wajib disertakan!"))
                        .build();
            }

            boolean ok = DbHelper.getInstance().simpanTelemetry(req);
            if (ok) {
                return Response.ok(ResponseEnt.ok("Telemetri berhasil dicatat", true)).build();
            } else {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseEnt.error("Gagal menyimpan data telemetri"))
                        .build();
            }
        } catch (Exception e) {
            LOG.severe("Error on receiveTelemetry: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }

    // ------------------------------------------------------------------------
    // 3. ENDPOINT DAFTAR DARURAT AKTIF (GET /api/iot/active-emergencies)
    // ------------------------------------------------------------------------
    // Dipanggil oleh: Aplikasi Android Caregiver / Dashboard Monitoring
    // Mengambil seluruh data kejadian darurat yang masih berstatus 'TERDETEKSI', 'COUNTDOWN',
    // 'AUTO_CALL', atau 'MENUNGGU' (belum dibatalkan / belum selesai ditangani).
    @GET
    @Path("/active-emergencies")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getActiveEmergencies() {
        try {
            List<KejadianDaruratEnt> list = DbHelper.getInstance().getActiveEmergencies();
            return Response.ok(ResponseEnt.ok("Daftar insiden darurat aktif", list)).build();
        } catch (Exception e) {
            LOG.severe("Error on getActiveEmergencies: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }

    // ------------------------------------------------------------------------
    // 4. ENDPOINT PEMBATALAN ALARM DARURAT (POST /api/iot/cancel?macAddress=...)
    // ------------------------------------------------------------------------
    // Dipanggil oleh:
    // 1. Raspberry Pi Pico W ketika lansia menekan tombol GP18 saat countdown berlangsung (false alarm).
    // 2. Aplikasi Android Caregiver saat tombol 'Batalkan Alarm' ditekan oleh perawat/keluarga.
    // Efek: Mengubah status_kejadian di 'tb_kejadian_darurat' menjadi 'DIBATALKAN'.
    @POST
    @Path("/cancel")
    @Produces(MediaType.APPLICATION_JSON)
    public Response cancelEmergency(@QueryParam("macAddress") String macAddress) {
        try {
            String mac = (macAddress != null && !macAddress.trim().isEmpty()) ? macAddress.trim() : "";
            boolean ok = DbHelper.getInstance().cancelEmergency(mac);
            if (ok) {
                return Response.ok(ResponseEnt.ok("Alarm darurat berhasil dibatalkan", "CANCELLED")).build();
            } else {
                return Response.ok(ResponseEnt.ok("Tidak ada insiden aktif yang perlu dibatalkan", "NO_ACTIVE_INCIDENT")).build();
            }
        } catch (Exception e) {
            LOG.severe("Error on cancelEmergency: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }

    // ------------------------------------------------------------------------
    // 5. ENDPOINT PENGAMBILAN KONFIGURASI DINAMIS (GET /api/iot/config)
    // ------------------------------------------------------------------------
    // Dipanggil oleh: Raspberry Pi Pico W saat booting awal atau sinkronisasi setting.
    // Mengembalikan: Ambang batas jatuh (SVM), sudut tilt, durasi hitung mundur buzzer (countdown),
    // batas detak jantung/SpO2, serta koordinat geofence dari tabel 'tb_pengaturan'.
    @GET
    @Path("/config")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConfig() {
        try {
            PengaturanEnt cfg = DbHelper.getInstance().getPengaturan();
            return Response.ok(ResponseEnt.ok("Konfigurasi sabuk IoT", cfg)).build();
        } catch (Exception e) {
            LOG.severe("Error on getConfig: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/config/{mac}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConfigByMac(@PathParam("mac") String mac) {
        try {
            PengaturanEnt cfg = DbHelper.getInstance().getPengaturan();
            return Response.ok(ResponseEnt.ok("Konfigurasi sabuk IoT", cfg)).build();
        } catch (Exception e) {
            LOG.severe("Error on getConfigByMac: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }

    // ------------------------------------------------------------------------
    // 6. ENDPOINT POLLING STATUS DARURAT OLEH PICO W (GET /api/iot/status/{mac})
    // ------------------------------------------------------------------------
    // Dipanggil oleh: Raspberry Pi Pico W saat sedang dalam mode bunyi buzzer darurat.
    // Pico W mengecek apakah caregiver sudah memencet tombol "Batalkan Alarm" dari HP.
    // Jika return isActive == false, Pico W akan langsung mematikan buzzer dan kembali ke mode normal.
    @GET
    @Path("/status/{mac}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStatusByMac(@PathParam("mac") String mac) {
        try {
            List<KejadianDaruratEnt> list = DbHelper.getInstance().getActiveEmergencies();
            boolean isActive = false;
            if (list != null && mac != null && !list.isEmpty()) {
                String cleanMac = mac.replace(":", "").replace("-", "").trim();
                for (KejadianDaruratEnt k : list) {
                    if (k.getPerangkat() != null) {
                        String devMac = k.getPerangkat().getMacAddress();
                        if (devMac != null) {
                            String cleanDevMac = devMac.replace(":", "").replace("-", "").trim();
                            if (cleanMac.equalsIgnoreCase(cleanDevMac)) {
                                isActive = true;
                                break;
                            }
                        }
                        // Fallback: jika id_perangkat == 1 (default device saat MAC belum terasosiasi di tb_perangkat)
                        if (k.getPerangkat().getIdPerangkat() == 1) {
                            isActive = true;
                            break;
                        }
                    } else {
                        // Jika perangkat null, anggap ada kejadian aktif
                        isActive = true;
                        break;
                    }
                }
            }
            return Response.ok(ResponseEnt.ok("Status darurat", isActive)).build();
        } catch (Exception e) {
            return Response.ok(ResponseEnt.ok("Status darurat", false)).build();
        }
    }

    // ------------------------------------------------------------------------
    // 7. ENDPOINT BACA DATA SENSOR TERAKHIR (GET /api/iot/sensor-latest/{id})
    // ------------------------------------------------------------------------
    // Mengambil 1 baris record data sensor paling baru dari perangkat tertentu
    // untuk keperluan grafik realtime di dashboard Android / Web.
    @GET
    @Path("/sensor-latest/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLatestSensorData(@PathParam("id") Integer id) {
        try {
            int devId = (id != null) ? id : 1;
            DataSensorEnt data = DbHelper.getInstance().getLatestSensorData(devId);
            if (data != null) {
                return Response.ok(ResponseEnt.ok("Data sensor terbaru", data)).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ResponseEnt.error("Data sensor belum tersedia"))
                        .build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }
}
