package vn.edu.parking.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.session.SessionRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.beans.factory.ObjectProvider;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.GateShiftAssignment;
import vn.edu.parking.domain.SecurityPermission;
import vn.edu.parking.repository.DesktopSessionRepository;
import vn.edu.parking.repository.GateShiftAssignmentRepository;
import vn.edu.parking.repository.SecurityAuditRepository;
import vn.edu.parking.repository.SecurityRoleRepository;
import vn.edu.parking.repository.SystemAccountRepository;
import vn.edu.parking.service.AccountManagementService;
import vn.edu.parking.service.AccountManagementService.EmployeeProfile;
import vn.edu.parking.service.GateShiftService;
import vn.edu.parking.service.DesktopRefreshTokenService;
import vn.edu.parking.service.SecurityAuditService;
import vn.edu.parking.service.AccountPrincipal;
import vn.edu.parking.service.AccountNotFoundException;
import vn.edu.parking.service.DuplicateAccountException;
import vn.edu.parking.service.LastManagementAccountException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;

@Controller
public class AccountManagementPageController {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private final SystemAccountRepository accounts;
    private final SecurityRoleRepository roles;
    private final SecurityAuditRepository audit;
    private final AccountManagementService management;
    private final DesktopSessionRepository desktopSessions;
    private final GateShiftAssignmentRepository shifts;
    private final GateShiftService shiftService;
    private final ObjectProvider<DesktopRefreshTokenService> refreshTokens;
    private final SessionRegistry webSessions;
    private final SecurityAuditService securityAudit;

    public AccountManagementPageController(SystemAccountRepository accounts, SecurityRoleRepository roles,
            SecurityAuditRepository audit, AccountManagementService management,
            DesktopSessionRepository desktopSessions, GateShiftAssignmentRepository shifts,
            GateShiftService shiftService, ObjectProvider<DesktopRefreshTokenService> refreshTokens,
            SessionRegistry webSessions, SecurityAuditService securityAudit) {
        this.accounts = accounts;
        this.roles = roles;
        this.audit = audit;
        this.management = management;
        this.desktopSessions = desktopSessions;
        this.shifts = shifts;
        this.shiftService = shiftService;
        this.refreshTokens = refreshTokens;
        this.webSessions = webSessions;
        this.securityAudit = securityAudit;
    }

    @GetMapping("/account-management")
    String accounts(Model model) {
        model.addAttribute("accounts", accounts.findAllByOrderByUsernameAsc());
        model.addAttribute("roles", roles.findByEnabledTrueOrderByDisplayNameAsc());
        model.addAttribute("gateStaff", accounts.findAllByOrderByUsernameAsc().stream()
            .filter(a -> a.isEnabled() && a.getSecurityRole() != null
                && a.getSecurityRole().getPermissions().contains(SecurityPermission.GATE_OPERATE)).toList());
        populateLiveModel(model);
        return "account-management";
    }

    @GetMapping("/account-management/live")
    String live(Model model) {
        populateLiveModel(model);
        return "account-management-live";
    }

    private void populateLiveModel(Model model) {
        var now = Instant.now();
        model.addAttribute("activeDesktopSessions", desktopSessions.findTop100ByOrderByCreatedAtDesc().stream()
            .filter(session -> session.isOnlineAt(now))
            .map(session -> {
                var currentShift = shifts
                    .findByAccount_IdAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByStartsAtDesc(
                        session.getAccount().getId(), now, now).stream().findFirst().orElse(null);
                return new ActiveSessionView(session.getSessionId(), session.getAccount().getUsername(), session.getDeviceLabel(),
                    local(session.getCreatedAt()), local(session.getLastSeenAt()),
                    currentShift == null ? null : currentShift.getShiftName(),
                    currentShift == null ? null : currentShift.getDuty());
            }).toList());
        model.addAttribute("activeWebSessions", webSessions.getAllPrincipals().stream()
            .filter(AccountPrincipal.class::isInstance)
            .map(AccountPrincipal.class::cast)
            .flatMap(principal -> webSessions.getAllSessions(principal, false).stream()
                .map(info -> new WebSessionView(info.getSessionId(), principal.getUsername(),
                    local(info.getLastRequest().toInstant()))))
            .toList());
        model.addAttribute("shiftAssignments", shifts.findTop50ByOrderByStartsAtDesc().stream()
            .map(shift -> new ShiftView(shift.getId(), shift.getAccount().getUsername(), shift.getShiftName(),
                local(shift.getStartsAt()), local(shift.getEndsAt()), shift.getDuty(), shift.getNotes(),
                !now.isBefore(shift.getStartsAt()) && now.isBefore(shift.getEndsAt())))
            .toList());
    }

    @PostMapping("/account-management/shifts")
    String assignShift(@RequestParam String username, @RequestParam String shiftName,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime startsAt,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime endsAt,
            @RequestParam String duty, @RequestParam(required = false) String notes,
            RedirectAttributes redirect) {
        try {
            shiftService.assign(username, shiftName, startsAt.atZone(VIETNAM).toInstant(),
                endsAt.atZone(VIETNAM).toInstant(), duty, notes);
            redirect.addFlashAttribute("message", "Đã phân ca cho " + username + ".");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/account-management";
    }

    @PostMapping("/account-management/shifts/delete")
    String deleteShift(@RequestParam long id, RedirectAttributes redirect) {
        try {
            shiftService.delete(id);
            redirect.addFlashAttribute("message", "Đã xóa phân công ca.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/account-management";
    }

    @PostMapping("/account-management/sessions/desktop/revoke")
    String revokeDesktopSession(@RequestParam String sessionId, RedirectAttributes redirect) {
        var tokenService = refreshTokens.getIfAvailable();
        if (tokenService != null && tokenService.revokeSession(sessionId)) {
            securityAudit.recordCurrent("SESSION_REVOKE", "DESKTOP_SESSION", sessionId,
                "SUCCESS", "ADMIN_REQUEST", null);
            redirect.addFlashAttribute("message", "Đã đăng xuất phiên Desktop.");
        } else redirect.addFlashAttribute("error", "Phiên Desktop không tồn tại hoặc đã kết thúc.");
        return "redirect:/account-management";
    }

    @PostMapping("/account-management/sessions/web/revoke")
    String revokeWebSession(@RequestParam String sessionId, HttpServletRequest request,
            RedirectAttributes redirect) {
        if (request.getSession(false) != null && request.getSession(false).getId().equals(sessionId)) {
            redirect.addFlashAttribute("error", "Không thể kết thúc phiên Web đang sử dụng.");
            return "redirect:/account-management";
        }
        var session = webSessions.getSessionInformation(sessionId);
        if (session == null || session.isExpired()) {
            redirect.addFlashAttribute("error", "Phiên Web không tồn tại hoặc đã kết thúc.");
        } else {
            session.expireNow();
            securityAudit.recordCurrent("SESSION_REVOKE", "WEB_SESSION", sessionId,
                "SUCCESS", "ADMIN_REQUEST", null);
            redirect.addFlashAttribute("message", "Đã kết thúc phiên Web.");
        }
        return "redirect:/account-management";
    }

    private static LocalDateTime local(Instant value) {
        return LocalDateTime.ofInstant(value, VIETNAM);
    }

    public record ActiveSessionView(String sessionId, String username, String deviceLabel, LocalDateTime loggedInAt,
            LocalDateTime lastSeenAt, String shiftName, String duty) { }
    public record WebSessionView(String sessionId, String username, LocalDateTime lastSeenAt) { }
    public record ShiftView(Long id, String username, String shiftName, LocalDateTime startsAt,
            LocalDateTime endsAt, String duty, String notes, boolean active) { }

    @PostMapping("/account-management/create")
    String create(@RequestParam String username, @RequestParam Long roleId,
            @RequestParam String fullName, @RequestParam String phone, @RequestParam String email,
            @RequestParam String citizenId, @RequestParam LocalDate dateOfBirth,
            @RequestParam String address,
            RedirectAttributes redirect) {
        try {
            var created = management.createAccount(username, roleId,
                new EmployeeProfile(fullName, phone, email, citizenId, dateOfBirth, address));
            redirect.addFlashAttribute("message", "Đã tạo tài khoản " + created.account().getUsername() + ".");
            redirect.addFlashAttribute("temporaryPassword", created.temporaryPassword());
        } catch (DuplicateAccountException ex) {
            redirect.addFlashAttribute("error", "Tên đăng nhập đã tồn tại.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/account-management";
    }

    @PostMapping("/account-management/profile")
    String profile(@RequestParam String username, @RequestParam Long roleId,
            @RequestParam String fullName, @RequestParam String phone, @RequestParam String email,
            @RequestParam String citizenId, @RequestParam LocalDate dateOfBirth,
            @RequestParam String address,
            RedirectAttributes redirect) {
        try {
            management.updateProfile(username, roleId,
                new EmployeeProfile(fullName, phone, email, citizenId, dateOfBirth, address));
            redirect.addFlashAttribute("message", "Đã cập nhật thông tin nhân viên.");
        } catch (LastManagementAccountException ex) {
            redirect.addFlashAttribute("error", "Không thể hạ quyền tài khoản quản trị vai trò cuối cùng.");
        } catch (AccountNotFoundException | IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/account-management";
    }

    @PostMapping("/account-management/state")
    String state(@RequestParam String username, @RequestParam boolean enabled,
            RedirectAttributes redirect) {
        try {
            if (enabled) management.enable(username); else management.disable(username);
            redirect.addFlashAttribute("message", enabled ? "Đã mở khóa tài khoản." : "Đã khóa tài khoản.");
        } catch (LastManagementAccountException ex) {
            redirect.addFlashAttribute("error", "Không thể khóa tài khoản quản trị cuối cùng.");
        } catch (AccountNotFoundException | IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", "Không tìm thấy tài khoản.");
        }
        return "redirect:/account-management";
    }

    @PostMapping("/account-management/reset-password")
    String resetPassword(@RequestParam String username, RedirectAttributes redirect) {
        try {
            String temporaryPassword = management.resetPassword(username);
            redirect.addFlashAttribute("message", "Đã đặt lại mật khẩu cho " + username + ".");
            redirect.addFlashAttribute("temporaryPassword", temporaryPassword);
        } catch (AccountNotFoundException | IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", "Không tìm thấy tài khoản.");
        }
        return "redirect:/account-management";
    }

    @GetMapping("/security-audit")
    String audit(@RequestParam(defaultValue = "0") int page, Model model) {
        int safePage = Math.max(0, page);
        var events = audit.findAllByOrderByOccurredAtDesc(PageRequest.of(safePage, 25));
        model.addAttribute("events", events);
        return "security-audit";
    }
}
