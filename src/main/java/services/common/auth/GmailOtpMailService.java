package services.common.auth;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class GmailOtpMailService {
    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final String SMTP_PORT = "587";
    private static final String ENV_GMAIL_USER = "APP_GMAIL_USERNAME";
    private static final String ENV_GMAIL_APP_PASSWORD = "APP_GMAIL_APP_PASSWORD";
    private static final String LOCAL_SECRETS_FILE = "local-secrets.properties";
    private static final String FILE_GMAIL_USER = "gmail.username";
    private static final String FILE_GMAIL_APP_PASSWORD = "gmail.appPassword";
    private static final String HARDCODED_GMAIL_USER = "";
    private static final String HARDCODED_GMAIL_APP_PASSWORD = "";

    public void sendOtpMail(String toEmail, String otp, int expirySeconds) throws MessagingException {
        Properties localSecrets = loadLocalSecrets();
        String gmailUser = resolveValue(HARDCODED_GMAIL_USER, System.getenv(ENV_GMAIL_USER), localSecrets.getProperty(FILE_GMAIL_USER));
        String gmailAppPassword = resolveValue(HARDCODED_GMAIL_APP_PASSWORD, System.getenv(ENV_GMAIL_APP_PASSWORD), localSecrets.getProperty(FILE_GMAIL_APP_PASSWORD));

        if (isBlank(gmailUser) || isBlank(gmailAppPassword)) {
            throw new MessagingException("Identifiants Gmail manquants: renseigner HARDCODED_*, variables d'environnement APP_GMAIL_* ou fichier local-secrets.properties");
        }

        Properties properties = new Properties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.host", SMTP_HOST);
        properties.put("mail.smtp.port", SMTP_PORT);

        Session session = Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(gmailUser, gmailAppPassword);
            }
        });

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(gmailUser));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
        message.setSubject("Votre code OTP - Fin Tokhroj");

        String body = "Bonjour,\n\n"
                + "Votre code OTP est: " + otp + "\n"
                + "Ce code expire dans " + expirySeconds + " secondes.\n\n"
                + "Si vous n'êtes pas à l'origine de cette demande, ignorez cet email.\n\n"
                + "Fin Tokhroj";
        message.setText(body);

        Transport.send(message);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String resolveValue(String hardcoded, String envValue, String fileValue) {
        if (!isBlank(hardcoded)) {
            return hardcoded;
        }
        if (!isBlank(envValue)) {
            return envValue;
        }
        return isBlank(fileValue) ? "" : fileValue.trim();
    }

    private Properties loadLocalSecrets() {
        Properties properties = new Properties();
        Path filePath = Paths.get(LOCAL_SECRETS_FILE);
        if (!Files.exists(filePath)) {
            return properties;
        }
        try (InputStream in = Files.newInputStream(filePath)) {
            properties.load(in);
        } catch (IOException ignored) {
        }
        return properties;
    }
}
