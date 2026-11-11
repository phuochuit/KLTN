package vn.edu.parking.service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Component;

/**
 * Ghi nhận thiết bị đã đăng nhập Web để trang quản trị hiển thị đúng
 * (máy tính hay điện thoại, trình duyệt nào).
 */
@Component
public class WebSessionDevices {
    public record Device(String label, String kind, String ip, Instant since) { }

    private final Map<String, Device> devices = new ConcurrentHashMap<>();

    public void register(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) {
            return;
        }
        devices.put(session.getId(),
            describe(request.getHeader("User-Agent"), clientIp(request)));
    }

    /**
     * Khôi phục metadata của phiên ở yêu cầu đầu tiên sau khi ứng dụng khởi động
     * lại, nhưng không ghi đè thời điểm đã ghi nhận từ lúc đăng nhập.
     */
    public void observe(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) {
            return;
        }
        devices.computeIfAbsent(session.getId(), ignored ->
            describe(request.getHeader("User-Agent"), clientIp(request)));
    }

    /** Giữ duy nhất phiên mới nhất nếu cùng tài khoản đăng nhập lại từ cùng IP. */
    public void expireOtherSessionsAtSameIp(HttpServletRequest request,
            Authentication authentication, SessionRegistry sessions) {
        var current = request.getSession(false);
        if (current == null || authentication == null) {
            return;
        }
        var currentDevice = devices.get(current.getId());
        if (currentDevice == null) {
            return;
        }
        sessions.getAllSessions(authentication.getPrincipal(), false).stream()
            .filter(info -> !current.getId().equals(info.getSessionId()))
            .filter(info -> {
                var device = devices.get(info.getSessionId());
                return device != null && currentDevice.ip().equals(device.ip());
            })
            .forEach(info -> info.expireNow());
    }

    public Device find(String sessionId) {
        return sessionId == null ? null : devices.get(sessionId);
    }

    @EventListener
    public void onSessionDestroyed(HttpSessionDestroyedEvent event) {
        if (event.getId() != null) {
            devices.remove(event.getId());
        }
    }

    static Device describe(String userAgent, String ip) {
        var agent = userAgent == null ? "" : userAgent.toLowerCase();
        String browser;
        if (agent.contains("edg/")) {
            browser = "Edge";
        } else if (agent.contains("opr/") || agent.contains("opera")) {
            browser = "Opera";
        } else if (agent.contains("samsungbrowser")) {
            browser = "Samsung Internet";
        } else if (agent.contains("crios") || agent.contains("chrome")) {
            browser = "Chrome";
        } else if (agent.contains("fxios") || agent.contains("firefox")) {
            browser = "Firefox";
        } else if (agent.contains("safari")) {
            browser = "Safari";
        } else {
            browser = "Trình duyệt";
        }

        String system;
        boolean phone;
        if (agent.contains("iphone")) {
            system = "iPhone";
            phone = true;
        } else if (agent.contains("ipad")) {
            system = "iPad";
            phone = true;
        } else if (agent.contains("android")) {
            system = "Android";
            phone = true;
        } else if (agent.contains("windows")) {
            system = "Windows";
            phone = false;
        } else if (agent.contains("macintosh") || agent.contains("mac os")) {
            system = "macOS";
            phone = false;
        } else if (agent.contains("linux")) {
            system = "Linux";
            phone = false;
        } else {
            system = "không rõ";
            phone = false;
        }

        return new Device(browser + " · " + system,
            phone ? "Điện thoại" : "Máy tính", ip, Instant.now());
    }

    private static String clientIp(HttpServletRequest request) {
        var forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
