package com.smartbelt.obj;

// ============================================================================
// GENERIC REST API RESPONSE WRAPPER (ResponseEnt<T>)
// ============================================================================
// File ini membungkus seluruh response JSON yang dikembalikan oleh Jersey REST API
// ke client (Aplikasi Android / Raspberry Pi Pico W) dengan format standar:
// {
//    "success": true / false,
//    "message": "Pesan status hasil eksekusi",
//    "data": { ... objek data dinamis ... }
// }
public class ResponseEnt<T> {
    private boolean success;
    private String message;
    private T data;

    public ResponseEnt() {
    }

    public ResponseEnt(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static <T> ResponseEnt<T> ok(String message, T data) {
        return new ResponseEnt<>(true, message, data);
    }

    public static <T> ResponseEnt<T> ok(T data) {
        return new ResponseEnt<>(true, "Berhasil", data);
    }

    public static <T> ResponseEnt<T> error(String message) {
        return new ResponseEnt<>(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
