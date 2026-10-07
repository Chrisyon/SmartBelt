package com.smartbelt.restjersey;

import com.smartbelt.db.DbHelper;
import com.smartbelt.obj.ResponseEnt;
import com.smartbelt.obj.UserEnt;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

// ============================================================================
// SERVICE MANAJEMEN USER (REST API JERSEY) - CRUD, APPROVAL & RELASI KELUARGA
// ============================================================================
// Base Path: /api/user
// File ini mengelola data seluruh pengguna sistem:
// - Admin Wisma (pengelola penuh sistem)
// - Caregiver / Perawat (pemantau lansia)
// - Keluarga (pemantau khusus lansia tertentu)
// - Lansia / Pengguna Belt (pemakai sabuk)
@Path("/user")
public class UserService {

    // ------------------------------------------------------------------------
    // 1. GET ALL USERS (GET /api/user?role=...)
    // ------------------------------------------------------------------------
    // Mengambil semua user, bisa difilter berdasarkan role.
    // Seluruh hash password dihapus (di-null-kan) sebelum dikirim ke response.
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllUsers(@QueryParam("role") String role) {
        try {
            List<UserEnt> list = DbHelper.getInstance().getAllUsers(role);
            for (UserEnt u : list) {
                u.setPassword(null);
            }
            return Response.ok(ResponseEnt.ok("Daftar user", list)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }

    // ------------------------------------------------------------------------
    // 2. GET USER DETAIL (GET /api/user/{id})
    // ------------------------------------------------------------------------
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserById(@PathParam("id") Integer id) {
        try {
            UserEnt u = DbHelper.getInstance().getUserById(id);
            if (u != null) {
                u.setPassword(null);
                return Response.ok(ResponseEnt.ok("Detail user", u)).build();
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

    // ------------------------------------------------------------------------
    // 3. CREATE USER OLEH ADMIN (POST /api/user)
    // ------------------------------------------------------------------------
    // Jika user dibuat langsung oleh Admin, status_aktif otomatis true (tanpa approval).
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createUser(UserEnt user) {
        try {
            if (user.getPassword() == null || user.getPassword().isEmpty()) {
                user.setPassword("123456");
            }
            if (user.getStatusAktif() == null) {
                user.setStatusAktif(true); // User dibuat oleh Admin langsung aktif
            }
            UserEnt created = DbHelper.getInstance().register(user);
            if (created != null) {
                created.setPassword(null);
                return Response.ok(ResponseEnt.ok("User berhasil dibuat", created)).build();
            }
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResponseEnt.error("Username atau Email sudah terdaftar"))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }

    // ------------------------------------------------------------------------
    // 4. UPDATE USER PROFILE (PUT /api/user/{id})
    // ------------------------------------------------------------------------
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateUser(@PathParam("id") Integer id, UserEnt user) {
        try {
            user.setIdUser(id);
            boolean ok = DbHelper.getInstance().updateUser(user);
            if (user.getIdPenghuniTarget() != null) {
                DbHelper.getInstance().setKeluargaPenghuni(id, user.getIdPenghuniTarget(), user.getHubunganKeluarga());
            }
            if (ok) {
                UserEnt updated = DbHelper.getInstance().getUserById(id);
                if (updated != null) updated.setPassword(null);
                return Response.ok(ResponseEnt.ok("User berhasil diperbarui", updated)).build();
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

    // ------------------------------------------------------------------------
    // 5. GET & UPDATE RELASI KELUARGA (GET / PUT /api/user/{id}/keluarga)
    // ------------------------------------------------------------------------
    // Menghubungkan akun bertipe KELUARGA dengan data lansia di 'tb_penghuni'.
    @GET
    @Path("/{id}/keluarga")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserKeluarga(@PathParam("id") Integer id) {
        try {
            java.util.Map<String, Object> data = DbHelper.getInstance().getKeluargaByUserId(id);
            return Response.ok(ResponseEnt.ok("Relasi keluarga pengguna", data)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }

    @PUT
    @Path("/{id}/keluarga")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateUserKeluarga(@PathParam("id") Integer id, UserEnt req) {
        try {
            int targetId = req.getIdPenghuniTarget() != null ? req.getIdPenghuniTarget() : 0;
            String hub = req.getHubunganKeluarga();
            boolean ok = DbHelper.getInstance().setKeluargaPenghuni(id, targetId, hub);
            if (ok) {
                java.util.Map<String, Object> data = DbHelper.getInstance().getKeluargaByUserId(id);
                return Response.ok(ResponseEnt.ok("Relasi keluarga berhasil diperbarui", data)).build();
            }
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResponseEnt.error("Gagal memperbarui relasi keluarga"))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseEnt.error(e.getMessage()))
                    .build();
        }
    }

    // ------------------------------------------------------------------------
    // 6. DEAKTIVASI USER (DELETE /api/user/{id})
    // ------------------------------------------------------------------------
    // Mengubah status_aktif menjadi 0 (Soft delete, tidak menghapus fisik record database).
    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deactivateUser(@PathParam("id") Integer id) {
        try {
            boolean ok = DbHelper.getInstance().deactivateUser(id);
            if (ok) {
                return Response.ok(ResponseEnt.ok("User berhasil dinonaktifkan", "OK")).build();
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

    // ------------------------------------------------------------------------
    // 7. APPROVAL AKUN OLEH ADMIN (PUT /api/user/{id}/approve)
    // ------------------------------------------------------------------------
    // Admin Wisma menyetujui akun baru yang mendaftar mandiri.
    // Mengubah tb_user.status_aktif = 1 dan mengaktifkan tb_penghuni jika terkait.
    @PUT
    @Path("/{id}/approve")
    @Produces(MediaType.APPLICATION_JSON)
    public Response approveUser(@PathParam("id") Integer id) {
        try {
            boolean ok = DbHelper.getInstance().approveUser(id);
            if (ok) {
                UserEnt updated = DbHelper.getInstance().getUserById(id);
                if (updated != null) updated.setPassword(null);
                return Response.ok(ResponseEnt.ok("Akun pengguna berhasil disetujui dan diaktifkan", updated)).build();
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
