package vn.edu.parking.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "gate_evidence_assets", uniqueConstraints =
    @UniqueConstraint(name = "uk_gate_evidence_asset_storage", columnNames = "storage_key"), indexes =
    @Index(name = "ix_gate_evidence_asset_evidence", columnList = "evidence_id,asset_role"))
public class GateEvidenceAsset {
    @Id @Column(length = 36, columnDefinition = "char(36)") private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evidence_id", nullable = false, columnDefinition = "char(36)")
    private GateEvidence evidence;
    @Enumerated(EnumType.STRING) @Column(name = "asset_role", nullable = false, length = 32) private EvidenceAssetRole assetRole;
    @Column(name = "storage_key", nullable = false, length = 255) private String storageKey;
    @Column(nullable = false, length = 64) private String sha256;
    @Column(name = "media_type", nullable = false, length = 80) private String mediaType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(name = "width_px") private Integer widthPx;
    @Column(name = "height_px") private Integer heightPx;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    protected GateEvidenceAsset() { }
    public GateEvidenceAsset(String id, GateEvidence evidence, EvidenceAssetRole assetRole,
            String storageKey, String sha256, String mediaType, long sizeBytes,
            Integer widthPx, Integer heightPx, Instant createdAt) {
        this.id = id; this.evidence = evidence; this.assetRole = assetRole;
        this.storageKey = storageKey; this.sha256 = sha256; this.mediaType = mediaType;
        this.sizeBytes = sizeBytes; this.widthPx = widthPx; this.heightPx = heightPx; this.createdAt = createdAt;
    }
    public void markDeleted(Instant now) { if (deletedAt == null) deletedAt = now; }
    public String getId() { return id; }
    public GateEvidence getEvidence() { return evidence; }
    public EvidenceAssetRole getAssetRole() { return assetRole; }
    public String getStorageKey() { return storageKey; }
    public String getSha256() { return sha256; }
    public String getMediaType() { return mediaType; }
    public long getSizeBytes() { return sizeBytes; }
    public Integer getWidthPx() { return widthPx; }
    public Integer getHeightPx() { return heightPx; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
