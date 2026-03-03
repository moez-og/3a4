package services.common.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OtpMemoryService {
    public enum VerificationStatus {
        SUCCESS,
        INVALID,
        EXPIRED,
        BLOCKED,
        REQUIRES_FACE_VERIFICATION,
        NOT_FOUND
    }

    public record VerificationResult(VerificationStatus status, int attempts, int attemptsLeft, long remainingBlockSeconds) {}

    private static final int MAX_ATTEMPTS = 3;
    private static final OtpMemoryService INSTANCE = new OtpMemoryService();

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, OtpSession> sessions = new ConcurrentHashMap<>();

    private OtpMemoryService() {}

    public static OtpMemoryService getInstance() {
        return INSTANCE;
    }

    public String createOtp(String email, int expirySeconds) {
        String normalizedEmail = normalize(email);
        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));

        OtpSession session = new OtpSession();
        session.email = normalizedEmail;
        session.otp = otp;
        session.expiresAtEpochSeconds = Instant.now().getEpochSecond() + Math.max(1, expirySeconds);
        session.attempts = 0;
        session.resetAuthorized = false;
        session.blockedUntilEpochSeconds = 0;
        sessions.put(normalizedEmail, session);
        return otp;
    }

    public VerificationResult verifyOtp(String email, String candidateOtp) {
        String normalizedEmail = normalize(email);
        OtpSession session = sessions.get(normalizedEmail);
        if (session == null) {
            return new VerificationResult(VerificationStatus.NOT_FOUND, 0, MAX_ATTEMPTS, 0);
        }

        long now = Instant.now().getEpochSecond();
        if (session.blockedUntilEpochSeconds > now) {
            long remaining = session.blockedUntilEpochSeconds - now;
            return new VerificationResult(VerificationStatus.BLOCKED, session.attempts, Math.max(0, MAX_ATTEMPTS - session.attempts), remaining);
        }

        if (now > session.expiresAtEpochSeconds) {
            return new VerificationResult(VerificationStatus.EXPIRED, session.attempts, Math.max(0, MAX_ATTEMPTS - session.attempts), 0);
        }

        if (session.attempts >= MAX_ATTEMPTS) {
            return new VerificationResult(VerificationStatus.REQUIRES_FACE_VERIFICATION, session.attempts, 0, 0);
        }

        if (session.otp.equals(candidateOtp)) {
            session.resetAuthorized = true;
            return new VerificationResult(VerificationStatus.SUCCESS, session.attempts, Math.max(0, MAX_ATTEMPTS - session.attempts), 0);
        }

        session.attempts++;
        int attemptsLeft = Math.max(0, MAX_ATTEMPTS - session.attempts);
        if (session.attempts >= MAX_ATTEMPTS) {
            return new VerificationResult(VerificationStatus.REQUIRES_FACE_VERIFICATION, session.attempts, attemptsLeft, 0);
        }

        return new VerificationResult(VerificationStatus.INVALID, session.attempts, attemptsLeft, 0);
    }

    public void authorizeReset(String email) {
        OtpSession session = sessions.get(normalize(email));
        if (session != null) {
            session.resetAuthorized = true;
        }
    }

    public boolean isResetAuthorized(String email) {
        OtpSession session = sessions.get(normalize(email));
        if (session == null) {
            return false;
        }
        return session.resetAuthorized;
    }

    public void setBlockedForSeconds(String email, int seconds) {
        OtpSession session = sessions.get(normalize(email));
        if (session != null) {
            session.blockedUntilEpochSeconds = Instant.now().getEpochSecond() + Math.max(1, seconds);
        }
    }

    public long getRemainingOtpSeconds(String email) {
        OtpSession session = sessions.get(normalize(email));
        if (session == null) {
            return 0;
        }
        long now = Instant.now().getEpochSecond();
        return Math.max(0, session.expiresAtEpochSeconds - now);
    }

    public long getRemainingBlockSeconds(String email) {
        OtpSession session = sessions.get(normalize(email));
        if (session == null) {
            return 0;
        }
        long now = Instant.now().getEpochSecond();
        return Math.max(0, session.blockedUntilEpochSeconds - now);
    }

    public void clear(String email) {
        sessions.remove(normalize(email));
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private static class OtpSession {
        String email;
        String otp;
        int attempts;
        long expiresAtEpochSeconds;
        long blockedUntilEpochSeconds;
        boolean resetAuthorized;
    }
}
