package vn.edu.parking.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.GateShiftAssignment;
import vn.edu.parking.domain.SecurityPermission;
import vn.edu.parking.repository.GateShiftAssignmentRepository;
import vn.edu.parking.repository.SystemAccountRepository;

@Service
public class GateShiftService {
    private final SystemAccountRepository accounts;
    private final GateShiftAssignmentRepository shifts;

    public GateShiftService(SystemAccountRepository accounts, GateShiftAssignmentRepository shifts) {
        this.accounts = accounts;
        this.shifts = shifts;
    }

    @Transactional
    public GateShiftAssignment assign(String username, String shiftName, Instant startsAt,
            Instant endsAt, String duty, String notes) {
        String normalizedUsername = normalize(username, 80, "Tên đăng nhập");
        String normalizedName = normalize(shiftName, 80, "Tên ca");
        String normalizedDuty = normalize(duty, 240, "Nhiệm vụ");
        String normalizedNotes = optional(notes, 500, "Ghi chú");
        if (startsAt == null || endsAt == null || !startsAt.isBefore(endsAt))
            throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu.");
        if (Duration.between(startsAt, endsAt).compareTo(Duration.ofHours(24)) > 0)
            throw new IllegalArgumentException("Một ca làm không được dài quá 24 giờ.");

        var account = accounts.findByUsername(normalizedUsername)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản nhân viên."));
        if (!account.isEnabled() || !canOperateGate(account))
            throw new IllegalArgumentException("Chỉ có thể phân ca cho tài khoản nhân viên cổng đang hoạt động.");
        if (shifts.countOverlapping(account.getId(), startsAt, endsAt) > 0)
            throw new IllegalArgumentException("Nhân viên đã có ca làm trùng thời gian.");

        return shifts.save(new GateShiftAssignment(account, normalizedName, startsAt,
            endsAt, normalizedDuty, normalizedNotes));
    }

    @Transactional
    public void delete(long id) {
        if (!shifts.existsById(id)) throw new IllegalArgumentException("Không tìm thấy ca làm.");
        shifts.deleteById(id);
    }

    private static boolean canOperateGate(vn.edu.parking.domain.SystemAccount account) {
        return account.getSecurityRole() == null
            ? account.getRole() == AccountRole.GATE_STAFF
            : account.getSecurityRole().isEnabled()
                && account.getSecurityRole().getPermissions().contains(SecurityPermission.GATE_OPERATE);
    }

    private static String normalize(String value, int maxLength, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength)
            throw new IllegalArgumentException(field + " không hợp lệ.");
        return normalized;
    }

    private static String optional(String value, int maxLength, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " quá dài.");
        return normalized.isEmpty() ? null : normalized;
    }
}
