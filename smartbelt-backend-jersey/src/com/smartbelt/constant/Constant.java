package com.smartbelt.constant;

// ============================================================================
// KONFIGURASI GLOBAL SISTEM SMART-BELT (DATABASE, TELEGRAM & EMAIL SMTP)
// ============================================================================
// File ini menyimpan seluruh konstanta kredensial terpusat untuk aplikasi.
// Jika ada perubahan database, akun email notifikasi, atau token bot Telegram,
// pengubahan cukup dilakukan di file ini saja tanpa perlu membongkar class lain.
public enum Constant {
    Connection("jdbc:mysql://127.0.0.1:3306/db_smart_belt?useSSL=false&serverTimezone=Asia/Jayapura&allowPublicKeyRetrieval=true&characterEncoding=UTF-8"),
    User("root"),           // Username database MySQL
    Password("your_mysql_password"),     // // use your own database password

    TelegramBotToken("your_telegram_bot_token"), // // use your own telegram bot token
    TelegramChatId("your_telegram_chat_id"),     // // use your own telegram chat id

    SmtpHost("smtp.gmail.com"),
    SmtpPort("587"),
    SmtpUser("your_email@gmail.com"), // // use your own email
    SmtpPassword("your_smtp_app_password"); // // use your own app password

    private String text;

    Constant(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }
}
