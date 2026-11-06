package vn.edu.parking.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ui_option", uniqueConstraints = @UniqueConstraint(name = "uk_ui_option_cat_val", columnNames = {"category", "option_value"}))
public class UiOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(name = "option_value", nullable = false, length = 160)
    private String value;

    @Column(name = "use_count", nullable = false)
    private int useCount = 1;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt = Instant.now();

    public UiOption() {}

    public UiOption(String category, String value) {
        this.category = category;
        this.value = value;
        this.useCount = 1;
        this.lastUsedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public int getUseCount() { return useCount; }
    public void setUseCount(int useCount) { this.useCount = useCount; }

    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
