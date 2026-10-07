package com.smartbelt.util;

import com.smartbelt.constant.Constant;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import com.google.gson.JsonObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

// ============================================================================
// HELPER NOTIFIKASI TELEGRAM BOT (ALERT DARURAT JATUH & SOS)
// ============================================================================
// File ini bertugas mengirimkan pesan alert instan ke bot Telegram perawat/caregiver.
// Menggunakan Thread Pool Executor agar pengiriman pesan berlangsung ASINKRON (non-blocking),
// sehingga tidak memperlambat response HTTP ke Raspberry Pi Pico W.
public class TelegramHelper {
    private static final Logger LOG = Logger.getLogger(TelegramHelper.class.getName());
    // Pool 3 worker thread untuk antrean notifikasi darurat
    private static final ExecutorService executor = Executors.newFixedThreadPool(3);
    private static final OkHttpClient client = new OkHttpClient();

    // ------------------------------------------------------------------------
    // METHOD: sendAlertAsync(messageText)
    // ------------------------------------------------------------------------
    // Fungsi:
    // Mengirim pesan berformat HTML ke endpoint:
    // POST https://api.telegram.org/bot<TOKEN>/sendMessage
    // Payload JSON: { "chat_id": "...", "text": "...", "parse_mode": "HTML" }
    public static void sendAlertAsync(String messageText) {
        executor.submit(() -> {
            try {
                String token = Constant.TelegramBotToken.toString();
                String chatId = Constant.TelegramChatId.toString();
                if (token == null || token.isEmpty() || chatId == null || chatId.isEmpty()) {
                    return;
                }

                String url = "https://api.telegram.org/bot" + token + "/sendMessage";
                JsonObject json = new JsonObject();
                json.addProperty("chat_id", chatId);
                json.addProperty("text", messageText);
                json.addProperty("parse_mode", "HTML");

                RequestBody body = RequestBody.create(
                        json.toString(),
                        MediaType.parse("application/json; charset=utf-8")
                );

                Request request = new Request.Builder().url(url).post(body).build();
                try (Response response = client.newCall(request).execute()) {
                    LOG.info("Telegram notification sent, HTTP response: " + response.code());
                }
            } catch (Exception e) {
                LOG.warning("Failed to send Telegram alert: " + e.getMessage());
            }
        });
    }
}
