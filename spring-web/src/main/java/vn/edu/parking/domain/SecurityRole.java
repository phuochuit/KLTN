package vn.edu.parking.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "security_roles", uniqueConstraints = {
    @UniqueConstraint(name = "uk_security_roles_code", columnNames = "code"),
    @UniqueConstraint(name = "uk_security_roles_employee_prefix", columnNames = "employee_prefix")
})
public class SecurityRole {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 48) private String code;
    @Column(name = "display_name", nullable = false, length = 120) private String displayName;
    @Column(name = "employee_prefix", nullable = false, length = 8) private String employeePrefix;
    @Column(length = 500) private String description;
    @Column(name = "system_role", nullable = false) private boolean systemRole;
    @Column(nullable = false) private boolean enabled = true;
    @Column(name = "next_employee_number", nullable = false) private long nextEmployeeNumber = 1;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "security_role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_security_role_permission",
            columnNames = {"role_id", "permission_code"}))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission_code", nullable = false, length = 64)
    private Set<SecurityPermission> permissions = EnumSet.noneOf(SecurityPermission.class);

    protected SecurityRole() { }

    public SecurityRole(String code, String displayName, String employeePrefix,
            String description, boolean systemRole, Set<SecurityPermission> permissions) {
        this.code = code;
        this.displayName = displayName;
        this.employeePrefix = employeePrefix;
        this.description = description;
        this.systemRole = systemRole;
        setPermissions(permissions);
        this.createdAt = Instant.now();
    }

    public String allocateEmployeeCode() {
        String value = employeePrefix + "-" + String.format("%03d", nextEmployeeNumber);
        nextEmployeeNumber++;
        return value;
    }

    public void update(String displayName, String description, boolean enabled,
            Set<SecurityPermission> permissions) {
        this.displayName = displayName;
        this.description = description;
        this.enabled = enabled;
        setPermissions(permissions);
    }

    private void setPermissions(Set<SecurityPermission> values) {
        permissions = values == null || values.isEmpty()
            ? EnumSet.noneOf(SecurityPermission.class) : EnumSet.copyOf(values);
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public String getEmployeePrefix() { return employeePrefix; }
    public String getDescription() { return description; }
    public boolean isSystemRole() { return systemRole; }
    public boolean isEnabled() { return enabled; }
    public long getNextEmployeeNumber() { return nextEmployeeNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public Set<SecurityPermission> getPermissions() { return Collections.unmodifiableSet(permissions); }
}
