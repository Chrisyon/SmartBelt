package com.smartbelt.restjersey;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

// ============================================================================
// FILTER CROSS-ORIGIN RESOURCE SHARING (CORS) - JERSEY PROVIDER
// ============================================================================
// File ini bertugas menginjeksikan header HTTP CORS pada setiap response yang
// dikembalikan oleh Jersey REST API.
// Mengapa ini penting?
// 1. Aplikasi frontend (Android Web, SPA React/Vue, Swagger UI, atau Ngrok tunnel)
//    berjalan di domain/port/host yang berbeda dengan server Tomcat (port 8088).
// 2. Tanpa filter ini, browser/HTTP client akan memblokir request API karena aturan
//    keamanan Same-Origin Policy (SOP).
// 3. Header yang disisipkan:
//    - Access-Control-Allow-Origin: Mengizinkan request dari origin manapun (*)
//    - Access-Control-Allow-Headers: Mengizinkan header autentikasi token, JSON content, dll.
//    - Access-Control-Allow-Methods: Mengizinkan semua verb HTTP (GET, POST, PUT, DELETE, OPTIONS).
@Provider
public class CORSFilter implements ContainerResponseFilter {

    /**
     * Method filter akan otomatis dieksekusi oleh container Jersey tepat sebelum
     * response HTTP dikirim kembali ke client/HP Android.
     */
    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        // Izinkan semua domain/origin mengakses API backend
        responseContext.getHeaders().add("Access-Control-Allow-Origin", "*");

        // Izinkan header HTTP kustom, seperti token otorisasi Bearer dan header bypass ngrok
        responseContext.getHeaders().add("Access-Control-Allow-Headers", "origin, content-type, accept, authorization, ngrok-skip-browser-warning");

        // Izinkan kredensial sesi jika dibutuhkan
        responseContext.getHeaders().add("Access-Control-Allow-Credentials", "true");

        // Izinkan metode/kata kerja HTTP standar RESTful API
        responseContext.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
    }
}
