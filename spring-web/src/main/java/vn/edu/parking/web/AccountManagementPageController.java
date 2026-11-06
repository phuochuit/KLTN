package vn.edu.parking.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
import vn.edu.parking.domain.SystemAccount;
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
import vn.edu.parking.service.WebSessionDevices;
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
    private final WebSessionDevices webDevices;

    public AccountManagementPageController(SystemAccountRepository accounts, SecurityRoleRepository roles,
            SecurityAuditRepository audit, AccountManagementService management,
            DesktopSessionRepository desktopSessions, GateShiftAssignmentRepository shifts,
            GateShiftService shiftService, ObjectProvider<DesktopRefreshTokenService> refreshTokens,
            SessionRegistry webSessions, SecurityAuditService securityAudit,
            WebSessionDevices webDevices) {
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
        this.webDevices = webDevices;
    }

    @GetMapping("/account-management")
    String accounts(Model model, Authentication authentication) {
        boolean canManageAccounts = hasAuthority(authentication, SecurityPermission.ACCOUNT_MANAGE.name())
            || hasAuthority(authentication, "ROLE_MANAGEMENT");
        boolean canManageShifts = hasAuthority(authentication, SecurityPermission.SHIFT_MANAGE.name())
            || hasAuthority(authentication, "ROLE_MANAGEMENT");
        addPageCapabilities(model, canManageAccounts, canManageShifts);
        if (!canManageAccounts) model.asMap().remove("temporaryPassword");
        if (canManageAccounts) {
            model.addAttribute("accounts", accounts.findAllByOrderByUsernameAsc().stream()
                .map(AccountManagementPageController::accountView).toList());
            model.addAttribute("roles", roles.findByEnabledTrueOrderByDisplayNameAsc().stream()
                .map(role -> new RoleOption(role.getId(), role.getDisplayName())).toList());
        }
        if (canManageShifts) {
            model.addAttribute("gateStaff", accounts.findEnabledUsernamesByPermission(SecurityPermission.GATE_OPERATE));
        }
        populateLiveModel(model, canManageAccounts, canManageShifts);
        return "account-management";
    }

    @GetMapping("/account-management/live")
    String live(Model model, Authentication authentication) {
        boolean canManageAccounts = hasAuthority(authentication, SecurityPermission.ACCOUNT_MANAGE.name())
            || hasAuthority(authentication, "ROLE_MANAGEMENT");
        boolean canManageShifts = hasAuthority(authentication, SecurityPermission.SHIFT_MANAGE.name())
            || hasAuthority(authentication, "ROLE_MANAGEMENT");
        model.asMap().remove("temporaryPassword");
        addPageCapabilities(model, canManageAccounts, canManageShifts);
        populateLiveModel(model, canManageAccounts, canManageShifts);
        return "account-management-live";
    }

    private static void addPageCapabilities(Model model, boolean canManageAccounts, boolean canManageShifts) {
        model.addAttribute("canManageAccounts", canManageAccounts);
        model.addAttribute("canManageSessions", canManageAccounts);
        model.addAttribute("canManageShifts", canManageShifts);
        model.addAttribute("canReadLiveData", canManageAccounts || canManageShifts);
    }

    private void populateLiveModel(Model model, boolean canManageAccounts, boolean canManageShifts) {
        var now = Instant.now();
        model.addAttribute("liveSnapshotAt", local(now).withNano(0));
        if (!canManageAccounts && !canManageShifts) return;
        if (canManageAccounts) {
            model.addAttribute("activeDesktopSessions", desktopSessions.findTop100ByOrderByCreatedAtDesc().stream()
                .filter(session -> session.isActiveAt(now))
                .map(session -> {
                    var currentShift = shifts
                        .findByAccount_IdAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByStartsAtDesc(
                            session.getAccount().getId(), now, now).stream().findFirst().orElse(null);
                    return new ActiveSessionView(session.getSessionId(), session.getAccount().getUsername(),
                        session.getDeviceLabel(), local(session.getCreatedAt()), local(session.getLastSeenAt()),
                        currentShift == null ? null : currentShift.getShiftName(),
                        currentShift == null ? null : currentShift.getDuty());
                }).toList());
            model.addAttribute("activeWebSessions", webSessions.getAllPrincipals().stream()
                .filter(AccountPrincipal.class::isInstance)
                .map(AccountPrincipal.class::cast)
                .flatMap(principal -> webSessions.getAllSessions(principal, false).stream()
                    .map(info -> {
                        var device = webDevices.find(info.getSessionId());
                        return new WebSessionView(info.getSessionId(), principal.getUsername(),
                            local(info.getLastRequest().toInstant()),
                            device == null ? "Kh\u00f4ng x\u00e1c \u0111\u1ecbnh" : device.label(),
                            device == null ? "" : device.kind(),
                            device == null ? "" : device.ip(),
                            device == null ? null : local(device.since()));
                    }))
                .toList());
        }
        if (canManageShifts) {
            model.addAttribute("shiftAssignments", shifts.findTop50ByOrderByStartsAtDesc().stream()
                .map(shift -> {
                    boolean upcoming = now.isBefore(shift.getStartsAt());
                    boolean active = !upcoming && now.isBefore(shift.getEndsAt());
                    String state = upcoming ? "Sắp diễn ra" : active ? "Đang diễn ra" : "Đã kết thúc";
                    return new ShiftView(shift.getId(), shift.getAccount().getUsername(), shift.getShiftName(),
                        local(shift.getStartsAt()), local(shift.getEndsAt()), shift.getDuty(), shift.getNotes(),
                        active, upcoming, state);
                })
                .toList());
        }
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    private static AccountManagementView accountView(SystemAccount account) {
        var role = account.getSecurityRole();
        return new AccountManagementView(account.getUsername(), account.getEmployeeCode(), account.getFullName(),
            account.getPhone(), account.getEmail(), account.getCitizenId(), account.getDateOfBirth(),
            account.getAddress(), role == null ? null : role.getId(),
            role == null ? account.getRole().name() : role.getDisplayName(),
            account.isEnabled(), account.isMustChangePassword());
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
        String revokedUsername = desktopSessions.findBySessionId(sessionId)
            .map(session -> session.getAccount().getUsername()).orElse(null);
        var tokenService = refreshTokens.getIfAvailable();
        if (tokenService != null && tokenService.revokeSession(sessionId)) {
            securityAudit.recordCurrent("SESSION_REVOKE", "DESKTOP_SESSION", sessionId,
                "SUCCESS", "ADMIN_REQUEST", null);
            if (revokedUsername != null)
                securityAudit.record(null, "LOGOUT", "DESKTOP_SESSION", revokedUsername,
                    "SUCCESS", "ADMIN_REQUEST", null);
            redirect.addFlashAttribute("message", "Đã đăng xuất phiên Desktop.");
            return "redirect:/account-management?session=ended";
        }
        redirect.addFlashAttribute("error", "Phiên Desktop không tồn tại hoặc đã kết thúc.");
        return "redirect:/account-management?session=missing";
    }

    @PostMapping("/account-management/sessions/web/revoke")
    String revokeWebSession(@RequestParam String sessionId, HttpServletRequest request,
            RedirectAttributes redirect) {
        var session = webSessions.getSessionInformation(sessionId);
        if (session == null || session.isExpired()) {
            redirect.addFlashAttribute("error", "Phiên Web không tồn tại hoặc đã kết thúc.");
            return "redirect:/account-management?session=missing";
        }
        String revokedUsername = session.getPrincipal() instanceof AccountPrincipal principal
            ? principal.getUsername() : null;
        var httpSession = request.getSession(false);
        boolean currentSession = httpSession != null && httpSession.getId().equals(sessionId);
        session.expireNow();
        securityAudit.recordCurrent("SESSION_REVOKE", "WEB_SESSION", sessionId,
            "SUCCESS", "ADMIN_REQUEST", null);
        if (revokedUsername != null)
            securityAudit.record(null, "LOGOUT", "WEB_SESSION", revokedUsername,
                "SUCCESS", "ADMIN_REQUEST", null);
        if (currentSession) {
            // Tự kết thúc phiên đang mở: đăng xuất ngay về màn hình đăng nhập.
            if (httpSession != null) httpSession.invalidate();
            return "redirect:/login?ended=1";
        }
        redirect.addFlashAttribute("message", "Đã kết thúc phiên Web.");
        return "redirect:/account-management?session=ended";
    }

    private static LocalDateTime local(Instant value) {
        return LocalDateTime.ofInstant(value, VIETNAM);
    }

    public record ActiveSessionView(String sessionId, String username, String deviceLabel, LocalDateTime loggedInAt,
            LocalDateTime lastSeenAt, String shiftName, String duty) { }
    public record WebSessionView(String sessionId, String username, LocalDateTime lastSeenAt,
            String device, String deviceKind, String ip, LocalDateTime loggedInAt) { }
    public record ShiftView(Long id, String username, String shiftName, LocalDateTime startsAt,
            LocalDateTime endsAt, String duty, String notes, boolean active, boolean upcoming, String state) { }
    public record AccountManagementView(String username, String employeeCode, String fullName, String phone,
            String email, String citizenId, LocalDate dateOfBirth, String address, Long roleId,
            String roleDisplayName, boolean enabled, boolean mustChangePassword) { }
    public record RoleOption(Long id, String displayName) { }

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
    String audit(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String from, @RequestParam(defaultValue = "") String to,
            @RequestParam(defaultValue = "") String actor, @RequestParam(defaultValue = "") String action,
            @RequestParam(defaultValue = "") String outcome, Model model) {
        actor = actor.trim(); action = action.trim(); outcome = outcome.trim();
        String error = null;
        LocalDate start = null, end = null;
        try {
            if ((!from.isBlank() && !from.matches("\\d{4}-\\d{2}-\\d{2}"))
                    || (!to.isBlank() && !to.matches("\\d{4}-\\d{2}-\\d{2}"))) throw new IllegalArgumentException();
            if (!from.isBlank()) start = LocalDate.parse(from);
            if (!to.isBlank()) end = LocalDate.parse(to);
            if ((start != null && end != null && start.isAfter(end)) || actor.length() > 80
                    || action.length() > 48 || outcome.length() > 16) throw new IllegalArgumentException();
        } catch (RuntimeException ex) { error = "Bộ lọc không hợp lệ. Hãy kiểm tra khoảng ngày và nội dung tìm kiếm."; }
        final Instant startInstant = start == null ? null : start.atStartOfDay(VIETNAM).toInstant();
        final Instant endInstant = end == null || end.equals(LocalDate.MAX) ? null : end.plusDays(1).atStartOfDay(VIETNAM).toInstant();
        final String actorTerm = actor.toLowerCase(java.util.Locale.ROOT);
        final String actionTerm = action.toLowerCase(java.util.Locale.ROOT);
        final String outcomeTerm = outcome.toLowerCase(java.util.Locale.ROOT);
        final boolean invalid = error != null;
        org.springframework.data.jpa.domain.Specification<vn.edu.parking.domain.SecurityAuditEvent> filter = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (invalid) predicates.add(cb.disjunction());
            if (startInstant != null) predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), startInstant));
            if (endInstant != null) predicates.add(cb.lessThan(root.get("occurredAt"), endInstant));
            if (!actorTerm.isBlank()) predicates.add(cb.like(cb.lower(root.get("actorUsername")), "%" + escapeLike(actorTerm) + "%", '\\'));
            if (!actionTerm.isBlank()) predicates.add(cb.like(cb.lower(root.get("action")), "%" + escapeLike(actionTerm) + "%", '\\'));
            if (!outcomeTerm.isBlank()) predicates.add(cb.equal(cb.lower(root.get("outcome")), outcomeTerm));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var auditSort = org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Order.desc("occurredAt"),
            org.springframework.data.domain.Sort.Order.desc("id"));
        int requestedPage = Math.max(0, page);
        var pageable = PageRequest.of(requestedPage, 25, auditSort);
        org.springframework.data.domain.Page<vn.edu.parking.domain.SecurityAuditEvent> events =
                org.springframework.data.domain.Page.empty(PageRequest.of(0, 25, auditSort));
        boolean auditUnavailable = false;
        if (error == null) {
            try {
                events = audit.findAll(filter, pageable);
                int availablePage = events.getTotalPages() == 0 ? 0
                        : Math.min(requestedPage, events.getTotalPages() - 1);
                if (availablePage != requestedPage) {
                    events = audit.findAll(filter, PageRequest.of(availablePage, 25, auditSort));
                }
            } catch (org.springframework.dao.DataAccessException ex) {
                auditUnavailable = true;
                events = org.springframework.data.domain.Page.empty(PageRequest.of(0, 25, auditSort));
            }
        }
        model.addAttribute("events", events);
        model.addAttribute("auditUnavailable", auditUnavailable);
        model.addAttribute("filterFrom", from); model.addAttribute("filterTo", to);
        model.addAttribute("filterActor", actor); model.addAttribute("filterAction", action);
        model.addAttribute("filterOutcome", outcome); model.addAttribute("filterError", error);
        return "security-audit";
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
