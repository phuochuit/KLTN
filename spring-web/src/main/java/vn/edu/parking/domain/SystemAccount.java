package vn.edu.parking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "system_accounts", uniqueConstraints =
    {@UniqueConstraint(name = "uk_system_accounts_username", columnNames = "username"),
     @UniqueConstraint(name = "uk_system_accounts_employee_code", columnNames = "employee_code"),
     @UniqueConstraint(name = "uk_system_accounts_email", columnNames = "email"),
     @UniqueConstraint(name = "uk_system_accounts_citizen_id", columnNames = "citizen_id")})
public class SystemAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(name = "full_name", length = 160)
    private String fullName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AccountRole role;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    private SecurityRole securityRole;

    @Column(name = "employee_code", length = 24) private String employeeCode;
    @Column(length = 20) private String phone;
    @Column(length = 160) private String email;
    @Column(name = "citizen_id", length = 20) private String citizenId;
    @Column(name = "date_of_birth") private LocalDate dateOfBirth;
    @Column(length = 500) private String address;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "security_version", nullable = false)
    private long securityVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SystemAccount() { }

    public SystemAccount(String username, String passwordHash, AccountRole role, boolean mustChangePassword) {
        this(username, username, passwordHash, role, mustChangePassword);
    }

    public SystemAccount(String username, String fullName, String passwordHash, AccountRole role,
            boolean mustChangePassword) {
        this.username = username;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.mustChangePassword = mustChangePassword;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getFullName() {
        return fullName == null || fullName.isBlank() ? username : fullName;
    }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public AccountRole getRole() { return role; }
    public void setRole(AccountRole role) { this.role = role; }
    public SecurityRole getSecurityRole() { return securityRole; }
    public void setSecurityRole(SecurityRole securityRole) { this.securityRole = securityRole; }
    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getCitizenId() { return citizenId; }
    public void setCitizenId(String citizenId) { this.citizenId = citizenId; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public long getSecurityVersion() { return securityVersion; }
    public void incrementSecurityVersion() { securityVersion++; }
    public Instant getCreatedAt() { return createdAt; }
}
