package vn.edu.parking.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class LoginAttemptThrottle {
    private static final int MAX_FAILED_ATTEMPTS = 10;
    private static final int MAX_TRACKED_CLIENTS = 10_000;
    private static final long WINDOW_NANOS = Duration.ofMinutes(15).toNanos();
    private final Map<String, AttemptWindow> attempts = new LinkedHashMap<>(16, 0.75f, true);

    public synchronized boolean isBlocked(String remoteAddress) {
        String client = clientKey(remoteAddress);
        AttemptWindow window = attempts.get(client);
        if (window == null) return false;
        if (System.nanoTime() - window.startedAtNanos() >= WINDOW_NANOS) {
            attempts.remove(client);
            return false;
        }
        return window.failedAttempts() >= MAX_FAILED_ATTEMPTS;
    }

    public synchronized void recordFailure(String remoteAddress) {
        String client = clientKey(remoteAddress);
        long now = System.nanoTime();
        AttemptWindow current = attempts.get(client);
        if (current == null || now - current.startedAtNanos() >= WINDOW_NANOS) {
            current = new AttemptWindow(now, 0);
        }
        attempts.put(client, new AttemptWindow(current.startedAtNanos(), current.failedAttempts() + 1));
        while (attempts.size() > MAX_TRACKED_CLIENTS) {
            attempts.remove(attempts.keySet().iterator().next());
        }
    }

    public synchronized void recordSuccess(String remoteAddress) {
        attempts.remove(clientKey(remoteAddress));
    }

    private static String clientKey(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) return "unknown";
        return remoteAddress.length() <= 64 ? remoteAddress : remoteAddress.substring(0, 64);
    }

    private record AttemptWindow(long startedAtNanos, int failedAttempts) { }
}
