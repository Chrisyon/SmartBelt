package com.smartbelt.restjersey;

import com.smartbelt.db.DbHelper;
import com.smartbelt.obj.*;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

// ============================================================================
// SERVICE PENGATURAN SABUK IOT (REST API JERSEY) - AMBANG BATAS & GEOFENCE
// ============================================================================
// Base Path: /api/pengaturan
@Path("/pengaturan")
public class PengaturanService {

    // ------------------------------------------------------------------------
    // 1. ENDPOINT AMBIL PENGATURAN GLOBAL SISTEM (GET /api/pengaturan)
    // ------------------------------------------------------------------------
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPengaturan() {
        try {
            PengaturanEnt cfg = DbHelper.getInstance().getPengaturan();
            return Response.ok(ResponseEnt.ok("Pengaturan sistem", cfg)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }

    // ------------------------------------------------------------------------
    // 2. ENDPOINT UPDATE PENGATURAN GLOBAL (PUT /api/pengaturan)
    // ------------------------------------------------------------------------
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updatePengaturan(PengaturanEnt cfg) {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(ResponseEnt.error("Akses ditolak: Pengaturan hanya dapat diubah melalui endpoint pengguna /api/pengaturan/user/{userId}."))
                .build();
    }

    // ------------------------------------------------------------------------
    // 3. ENDPOINT AMBIL PENGATURAN BERDASARKAN USER (GET /api/pengaturan/user/{userId})
    // ------------------------------------------------------------------------
    @GET
    @Path("/user/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPengaturanByUser(@PathParam("userId") Integer userId) {
        try {
            PenghuniEnt penghuni = null;
            if (userId != null && userId > 0) {
                UserEnt user = DbHelper.getInstance().getUserById(userId);
                if (user != null) {
                    String role = user.getRole() != null ? user.getRole().toUpperCase() : "";
                    if ("PENGGUNA_BELT".equals(role) || "LANSIA".equals(role)) {
                        penghuni = DbHelper.getInstance().getPenghuniByLansia(userId);
                    } else {
                        List<PenghuniEnt> list = DbHelper.getInstance().getPenghuniByKeluarga(userId);
                        if (list != null && !list.isEmpty()) {
                            penghuni = list.get(0);
                        }
                    }
                }
            }

            int idPerangkat = 1;
            if (penghuni != null) {
                PerangkatEnt dev = DbHelper.getInstance().getPerangkatByPenghuni(penghuni.getIdPenghuni());
                if (dev == null) {
                    List<PerangkatEnt> allDevs = DbHelper.getInstance().getAllPerangkat();
                    if (allDevs != null && !allDevs.isEmpty()) {
                        dev = allDevs.get(0);
                        DbHelper.getInstance().updatePenghuniPerangkat(penghuni.getIdPenghuni(), dev.getIdPerangkat());
                    }
                }
                if (dev != null) {
                    idPerangkat = dev.getIdPerangkat();
                }
            } else {
                List<PerangkatEnt> allDevs = DbHelper.getInstance().getAllPerangkat();
                if (allDevs != null && !allDevs.isEmpty()) {
                    idPerangkat = allDevs.get(0).getIdPerangkat();
                }
            }

            PengaturanEnt cfg = DbHelper.getInstance().getPengaturanByPerangkat(idPerangkat);
            if (cfg == null) {
                cfg = DbHelper.getInstance().getPengaturan();
            }
            return Response.ok(ResponseEnt.ok("Pengaturan perangkat pengguna", cfg)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }

    // ------------------------------------------------------------------------
    // 4. ENDPOINT SIMPAN PENGATURAN SABUK (PUT /api/pengaturan/user/{userId})
    // ------------------------------------------------------------------------
    @PUT
    @Path("/user/{userId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updatePengaturanByUser(@PathParam("userId") Integer userId, PengaturanEnt cfg) {
        try {
            PenghuniEnt penghuni = null;
            if (userId != null && userId > 0) {
                UserEnt user = DbHelper.getInstance().getUserById(userId);
                if (user != null) {
                    String role = user.getRole() != null ? user.getRole().toUpperCase() : "";
                    if ("PENGGUNA_BELT".equals(role) || "LANSIA".equals(role)) {
                        penghuni = DbHelper.getInstance().getPenghuniByLansia(userId);
                    } else {
                        List<PenghuniEnt> list = DbHelper.getInstance().getPenghuniByKeluarga(userId);
                        if (list != null && !list.isEmpty()) {
                            penghuni = list.get(0);
                        }
                    }
                }
            }

            int idPerangkat = 1;
            if (penghuni != null) {
                PerangkatEnt dev = DbHelper.getInstance().getPerangkatByPenghuni(penghuni.getIdPenghuni());
                if (dev == null) {
                    List<PerangkatEnt> allDevs = DbHelper.getInstance().getAllPerangkat();
                    if (allDevs != null && !allDevs.isEmpty()) {
                        dev = allDevs.get(0);
                        DbHelper.getInstance().updatePenghuniPerangkat(penghuni.getIdPenghuni(), dev.getIdPerangkat());
                    }
                }
                if (dev != null) {
                    idPerangkat = dev.getIdPerangkat();
                }
            } else {
                List<PerangkatEnt> allDevs = DbHelper.getInstance().getAllPerangkat();
                if (allDevs != null && !allDevs.isEmpty()) {
                    idPerangkat = allDevs.get(0).getIdPerangkat();
                }
            }

            // Update ke tabel 'tb_pengaturan'
            boolean ok = DbHelper.getInstance().updatePengaturanByPerangkat(idPerangkat, cfg);
            if (ok) {
                PengaturanEnt updated = DbHelper.getInstance().getPengaturanByPerangkat(idPerangkat);
                return Response.ok(ResponseEnt.ok("Pengaturan berhasil disimpan", updated)).build();
            }
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error("Gagal menyimpan pengaturan ke database"))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }
}
