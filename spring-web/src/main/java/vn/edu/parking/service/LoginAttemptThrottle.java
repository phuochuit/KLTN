package vn.edu.parking.service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptThrottle {
    static final int MAX_FAILED_ATTEMPTS = 10;
    private static final int MAX_TRACKED_KEYS = 10_000;
    private static final long WINDOW_NANOS = Duration.ofMinutes(15).toNanos();
    private final Map<String, AttemptWindow> attempts = new LinkedHashMap<>(16, 0.75f, true);

    public synchronized boolean isBlocked(String username, String remoteAddress) {
        String key = clientKey(username, remoteAddress);
        AttemptWindow window = attempts.get(key);
        if (window == null) return false;
        if (System.nanoTime() - window.startedAtNanos() >= WINDOW_NANOS) {
            attempts.remove(key);
            return false;
        }
        return window.failedAttempts() >= MAX_FAILED_ATTEMPTS;
    }

    public synchronized void recordFailure(String username, String remoteAddress) {
        String key = clientKey(username, remoteAddress);
        long now = System.nanoTime();
        AttemptWindow current = attempts.get(key);
        if (current == null || now - current.startedAtNanos() >= WINDOW_NANOS)
            current = new AttemptWindow(now, 0);
        attempts.put(key, new AttemptWindow(current.startedAtNanos(), current.failedAttempts() + 1));
        while (attempts.size() > MAX_TRACKED_KEYS)
            attempts.remove(attempts.keySet().iterator().next());
    }

    public synchronized void recordSuccess(String username, String remoteAddress) {
        attempts.remove(clientKey(username, remoteAddress));
    }

    private static String clientKey(String username, String remoteAddress) {
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (normalizedUsername.length() > 80) normalizedUsername = normalizedUsername.substring(0, 80);
        String address = remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
        if (address.length() > 64) address = address.substring(0, 64);
        return address + "\n" + normalizedUsername;
    }

    private record AttemptWindow(long startedAtNanos, int failedAttempts) { }
}
