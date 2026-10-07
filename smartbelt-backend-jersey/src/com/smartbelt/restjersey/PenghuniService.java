package com.smartbelt.restjersey;

import com.smartbelt.db.DbHelper;
import com.smartbelt.obj.KontakDaruratEnt;
import com.smartbelt.obj.PenghuniEnt;
import com.smartbelt.obj.ResponseEnt;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

// ============================================================================
// SERVICE MANAJEMEN PENGHUNI & KONTAK DARURAT (REST API JERSEY)
// ============================================================================
// Base Path: /api/penghuni
// File ini menangani data lansia (penghuni wisma) dan daftar kontak darurat keluarga:
// 1. Data profil lansia (nama, tgl lahir, jenis kelamin, kamar, riwayat penyakit)
// 2. Relasi lansia dengan akun keluarga & akun lansia
// 3. Manajemen nomor telepon kontak darurat (penerima panggilan/SMS darurat)
@Path("/penghuni")
public class PenghuniService {

    // ------------------------------------------------------------------------
    // 1. GET SEMUA PENGHUNI (GET /api/penghuni)
    // ------------------------------------------------------------------------
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllPenghuni() {
        try {
            List<PenghuniEnt> list = DbHelper.getInstance().getAllPenghuni();
            return Response.ok(ResponseEnt.ok("Data penghuni berhasil dimuat", list)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }

    // ------------------------------------------------------------------------
    // 2. GET DETAIL PENGHUNI BY ID (GET /api/penghuni/{id})
    // ------------------------------------------------------------------------
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPenghuniById(@PathParam("id") Integer id) {
        try {
            PenghuniEnt p = DbHelper.getInstance().getPenghuniById(id);
            if (p != null) {
                return Response.ok(ResponseEnt.ok("Detail penghuni", p)).build();
            }
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ResponseEnt.error("Penghuni tidak ditemukan"))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }

    // ------------------------------------------------------------------------
    // 3. TAMBAH PENGHUNI BARU (POST /api/penghuni)
    // ------------------------------------------------------------------------
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createPenghuni(PenghuniEnt p) {
        try {
            if (p.getNamaLengkap() == null || p.getNamaLengkap().trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ResponseEnt.error("Nama lengkap penghuni wajib diisi"))
                        .build();
            }
            PenghuniEnt created = DbHelper.getInstance().addPenghuni(p);
            return Response.ok(ResponseEnt.ok("Penghuni berhasil ditambahkan", created)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }

    // ------------------------------------------------------------------------
    // 4. UPDATE DATA PENGHUNI (PUT /api/penghuni/{id})
    // ------------------------------------------------------------------------
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updatePenghuni(@PathParam("id") Integer id, PenghuniEnt p) {
        try {
            p.setIdPenghuni(id);
            boolean ok = DbHelper.getInstance().updatePenghuni(p);
            if (ok) {
                PenghuniEnt updated = DbHelper.getInstance().getPenghuniById(id);
                return Response.ok(ResponseEnt.ok("Data penghuni berhasil diperbarui", updated)).build();
            }
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ResponseEnt.error("Penghuni tidak ditemukan"))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }

    // ------------------------------------------------------------------------
    // 5. DEAKTIVASI PENGHUNI (DELETE /api/penghuni/{id})
    // ------------------------------------------------------------------------
    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deletePenghuni(@PathParam("id") Integer id) {
        try {
            boolean ok = DbHelper.getInstance().deletePenghuni(id);
            if (ok) {
                return Response.ok(ResponseEnt.ok("Penghuni berhasil dinonaktifkan", "OK")).build();
            }
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ResponseEnt.error("Penghuni tidak ditemukan"))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }

    // ------------------------------------------------------------------------
    // 6. AMBIL PENGHUNI BERDASARKAN KELUARGA (GET /api/penghuni/keluarga/{userId})
    // ------------------------------------------------------------------------
    // Dipakai saat akun KELUARGA login agar aplikasi HP hanya menampilkan
    // lansia yang terdaftar sebagai anggota keluarganya.
    @GET
    @Path("/keluarga/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPenghuniByKeluarga(@PathParam("userId") Integer userId) {
        try {
            List<PenghuniEnt> list = DbHelper.getInstance().getPenghuniByKeluarga(userId);
            return Response.ok(ResponseEnt.ok("Penghuni terpaut keluarga", list)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }

    // ------------------------------------------------------------------------
    // 7. AMBIL DATA PENGHUNI DARI AKUN LANSIA (GET /api/penghuni/lansia/{userId})
    // ------------------------------------------------------------------------
    @GET
    @Path("/lansia/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPenghuniByLansia(@PathParam("userId") Integer userId) {
        try {
            PenghuniEnt p = DbHelper.getInstance().getPenghuniByLansia(userId);
            if (p != null) {
                return Response.ok(ResponseEnt.ok("Penghuni akun lansia", p)).build();
            }
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ResponseEnt.error("Lansia tidak ditemukan"))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }

    // ------------------------------------------------------------------------
    // 8. MANAJEMEN KONTAK DARURAT (GET, POST, PUT, DELETE)
    // ------------------------------------------------------------------------
    // Mengelola daftar kontak telepon keluarga yang dihubungi jika terjadi darurat.
    @GET
    @Path("/{id}/kontak")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getKontakByPenghuni(@PathParam("id") Integer id) {
        try {
            List<KontakDaruratEnt> list = DbHelper.getInstance().getKontakByPenghuni(id);
            return Response.ok(ResponseEnt.ok("Daftar kontak darurat", list)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/{id}/kontak")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addKontak(@PathParam("id") Integer id, KontakDaruratEnt k) {
        try {
            KontakDaruratEnt created = DbHelper.getInstance().addKontak(id, k);
            return Response.ok(ResponseEnt.ok("Kontak berhasil ditambahkan", created)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }

    @PUT
    @Path("/kontak/{idKontak}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateKontak(@PathParam("idKontak") Integer idKontak, KontakDaruratEnt k) {
        try {
            boolean ok = DbHelper.getInstance().updateKontak(idKontak, k);
            if (ok) {
                return Response.ok(ResponseEnt.ok("Kontak berhasil diperbarui", k)).build();
            }
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ResponseEnt.error("Kontak tidak ditemukan"))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }

    @DELETE
    @Path("/kontak/{idKontak}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteKontak(@PathParam("idKontak") Integer idKontak) {
        try {
            boolean ok = DbHelper.getInstance().deleteKontak(idKontak);
            if (ok) {
                return Response.ok(ResponseEnt.ok("Kontak berhasil dinonaktifkan", "OK")).build();
            }
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ResponseEnt.error("Kontak tidak ditemukan"))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }
}
