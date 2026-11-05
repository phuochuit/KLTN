package vn.edu.parking.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.SecurityAuditEvent;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.repository.GateShiftAssignmentRepository;
import vn.edu.parking.repository.SecurityAuditRepository;
import vn.edu.parking.repository.SystemAccountRepository;

/**
 * Cung cấp dòng sự kiện đăng nhập/đăng xuất để Web hiển thị thông báo
 * ở góc dưới bên phải. Dữ liệu lấy từ nhật ký bảo mật nên không cần
 * thêm bảng mới và vẫn còn sau khi khởi động lại máy chủ.
 */
@Service
public class AuthActivityService {
    private static final Set<String> RELEVANT_ACTIONS = Set.of("LOGIN_SUCCESS", "LOGOUT");
    private final SecurityAuditRepository events;
    private final SystemAccountRepository accounts;
    private final GateShiftAssignmentRepository shifts;

    public AuthActivityService(SecurityAuditRepository events, SystemAccountRepository accounts,
            GateShiftAssignmentRepository shifts) {
        this.events = events;
        this.accounts = accounts;
        this.shifts = shifts;
    }

    public record Activity(Long id, String action, String platform, String username, String fullName,
            String position, String shiftName, long occurredAt) { }

    public record Feed(long cursor, List<Activity> events) { }

    /** Mốc hiện tại: dùng khi mở trang để không phát lại thông báo cũ. */
    @Transactional(readOnly = true)
    public Feed latest() {
        long cursor = events.findTop1ByOrderByIdDesc().map(SecurityAuditEvent::getId).orElse(0L);
        return new Feed(cursor, List.of());
    }

    @Transactional(readOnly = true)
    public Feed since(long afterId) {
        long cursor = Math.max(0, afterId);
        List<Activity> result = new ArrayList<>();
        for (SecurityAuditEvent row : events.findTop100ByActionInAndIdGreaterThanOrderByIdAsc(
                RELEVANT_ACTIONS, cursor)) {
            cursor = Math.max(cursor, row.getId());
            Activity activity = map(row);
            if (activity != null) result.add(activity);
        }
        return new Feed(cursor, result);
    }

    private Activity map(SecurityAuditEvent row) {
        String action = "LOGIN_SUCCESS".equals(row.getAction()) ? "LOGIN" : "LOGOUT";
        String targetType = row.getTargetType() == null ? "" : row.getTargetType();
        String platform = targetType.contains("DESKTOP") ? "WINFORM"
            : targetType.contains("WEB") ? "WEB"
            : "LOGIN".equals(action) ? "WEB" : null;
        if (platform == null) return null;

        String username = row.getActorUsername();
        if (username == null || username.isBlank()) username = row.getTargetReference();
        if (username == null || username.isBlank()) return null;

        SystemAccount account = accounts.findByUsername(username).orElse(null);
        String fullName = account == null || account.getFullName() == null || account.getFullName().isBlank()
            ? username : account.getFullName();
        String position = account == null || account.getSecurityRole() == null
            ? null : account.getSecurityRole().getDisplayName();
        var shift = account == null ? null : shifts
            .findByAccount_IdAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByStartsAtDesc(
                account.getId(), row.getOccurredAt(), row.getOccurredAt())
            .stream().findFirst().orElse(null);
        String shiftName = shift == null ? null : shift.getShiftName();
        if (position == null && shift != null) position = shift.getDuty();
        if (position == null || position.isBlank()) position = "Nhân viên";

        return new Activity(row.getId(), action, platform, username, fullName, position, shiftName,
            row.getOccurredAt().toEpochMilli());
    }
}
