package com.smartbelt.util;

import com.smartbelt.constant.Constant;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

// ============================================================================
// HELPER PENGIRIMAN EMAIL SMTP (RESET PASSWORD & KODE OTP)
// ============================================================================
// File ini bertugas mengirimkan email transaksi (seperti kode OTP reset password)
// menggunakan protokol SMTP Gmail (TLS Port 587) secara ASINKRON melalui ExecutorService.
public class EmailHelper {
    private static final Logger LOG = Logger.getLogger(EmailHelper.class.getName());
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);

    // ------------------------------------------------------------------------
    // METHOD: sendEmailAsync(toEmail, subject, contentHtml)
    // ------------------------------------------------------------------------
    // Mengirim email HTML asinkron ke penerima tanpa memblokir request pengguna.
    public static void sendEmailAsync(String toEmail, String subject, String contentHtml) {
        executor.submit(() -> {
            try {
                String host = Constant.SmtpHost.toString();
                String port = Constant.SmtpPort.toString();
                String user = Constant.SmtpUser.toString();
                String pass = Constant.SmtpPassword.toString();

                if (user == null || user.isEmpty() || pass == null || pass.isEmpty()) {
                    LOG.warning("SMTP credentials not configured.");
                    return;
                }

                Properties props = new Properties();
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.host", host);
                props.put("mail.smtp.port", port);

                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(user, pass);
                    }
                });

                // 3. Susun amplop dan isi email (MIME Message HTML)
                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(user, "Smart-Belt IoT System"));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
                message.setSubject(subject);
                message.setContent(contentHtml, "text/html; charset=utf-8");

                // 4. Kirim email melalui protokol SMTP
                Transport.send(message);
                LOG.info("Email successfully sent to: " + toEmail);
            } catch (Exception e) {
                LOG.warning("Failed to send email to " + toEmail + ": " + e.getMessage());
            }
        });
    }
}
