package vn.edu.parking.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.SecurityAuditEvent;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.repository.SecurityAuditRepository;
import vn.edu.parking.repository.SystemAccountRepository;
import vn.edu.parking.service.AccountManagementService;
import vn.edu.parking.service.DuplicateAccountException;
import vn.edu.parking.web.dto.AccountCreatedResponse;
import vn.edu.parking.web.dto.AccountSummaryResponse;
import vn.edu.parking.web.dto.PasswordResetResponse;
import vn.edu.parking.web.dto.SecurityAuditEventResponse;
import vn.edu.parking.web.dto.SecurityAuditPageResponse;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/account-admin")
@PreAuthorize("hasAuthority('ROLE_MANAGEMENT')")
public class AccountAdministrationController {
    private final SystemAccountRepository accounts;
    private final SecurityAuditRepository auditEvents;
    private final AccountManagementService management;

    public AccountAdministrationController(SystemAccountRepository accounts,
            SecurityAuditRepository auditEvents, AccountManagementService management) {
        this.accounts = accounts;
        this.auditEvents = auditEvents;
        this.management = management;
    }

    @GetMapping("/accounts")
    public List<AccountSummaryResponse> accounts() {
        return accounts.findAllByOrderByUsernameAsc().stream().map(AccountAdministrationController::summary).toList();
    }

    @GetMapping("/accounts/{username}")
    public AccountSummaryResponse account(@PathVariable String username) {
        return accounts.findByUsername(normalize(username)).map(AccountAdministrationController::summary)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping("/accounts")
    public ResponseEntity<?> create(@Valid @RequestBody CreateAccountRequest request) {
        try {
            var created = management.createAccount(request.username(), request.role());
            return ResponseEntity.status(HttpStatus.CREATED).body(new AccountCreatedResponse(
                summary(created.account()), created.temporaryPassword()));
        } catch (DuplicateAccountException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "account_exists"));
        }
    }

    @PostMapping("/accounts/{username}/disable")
    public ResponseEntity<Void> disable(@PathVariable String username) {
        management.disable(username);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/accounts/{username}/enable")
    public ResponseEntity<Void> enable(@PathVariable String username) {
        management.enable(username);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/accounts/{username}/role")
    public ResponseEntity<Void> changeRole(@PathVariable String username,
            @Valid @RequestBody ChangeRoleRequest request) {
        management.changeRole(username, request.role());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/accounts/{username}/password-reset")
    public PasswordResetResponse resetPassword(@PathVariable String username) {
        return new PasswordResetResponse(management.resetPassword(username), true);
    }

    @GetMapping("/audit")
    public SecurityAuditPageResponse audit(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return auditPage(auditEvents.findAllByOrderByOccurredAtDesc(pageRequest(page, size)));
    }

    @GetMapping("/audit/overrides")
    public SecurityAuditPageResponse overrides(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return auditPage(auditEvents.findByActionStartingWithOrderByOccurredAtDesc(
            "OVERRIDE_", pageRequest(page, size)));
    }

    private static PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page bounds");
        return PageRequest.of(page, size);
    }

    private static SecurityAuditPageResponse auditPage(Page<SecurityAuditEvent> page) {
        List<SecurityAuditEventResponse> events = page.getContent().stream()
            .map(event -> new SecurityAuditEventResponse(event.getId(), event.getActorAccountId(),
                event.getActorUsername(), event.getAction(), event.getTargetType(), event.getTargetReference(),
                event.getOutcome(), event.getReason(), event.getEvidenceReference(), event.getOccurredAt()))
            .toList();
        return new SecurityAuditPageResponse(events, page.getNumber(), page.getSize(),
            page.getTotalElements(), page.getTotalPages());
    }

    private static AccountSummaryResponse summary(SystemAccount account) {
        return new AccountSummaryResponse(account.getId(), account.getUsername(), account.getRole(),
            account.isEnabled(), account.isMustChangePassword());
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    public record CreateAccountRequest(@NotBlank @Size(max = 80) String username, @NotNull AccountRole role) { }
    public record ChangeRoleRequest(@NotNull AccountRole role) { }
}
