package vn.edu.parking.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "parking_operations", uniqueConstraints = @UniqueConstraint(
    name = "uk_parking_operations_actor_type_key",
    columnNames = { "actor_username", "operation_type", "operation_key" }))
public class ParkingOperation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_username", nullable = false, length = 100)
    private String actorUsername;

    @Column(name = "operation_type", nullable = false, length = 32)
    private String operationType;

    @Column(name = "operation_key", nullable = false, length = 36)
    private String operationKey;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ParkingOperationStatus status;

    @Lob
    @Column(name = "response_json", columnDefinition = "LONGTEXT")
    private String responseJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ParkingOperation() { }

    public ParkingOperation(String actorUsername, String operationType, String operationKey,
            String payloadHash, Instant now) {
        this.actorUsername = actorUsername;
        this.operationType = operationType;
        this.operationKey = operationKey;
        this.payloadHash = payloadHash;
        this.status = ParkingOperationStatus.PROCESSING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void complete(String responseJson, Instant now) {
        this.responseJson = responseJson;
        this.status = ParkingOperationStatus.COMPLETED;
        this.updatedAt = now;
    }

    public void restart(String payloadHash, Instant now) {
        this.payloadHash = payloadHash;
        this.responseJson = null;
        this.status = ParkingOperationStatus.PROCESSING;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getActorUsername() { return actorUsername; }
    public String getOperationType() { return operationType; }
    public String getOperationKey() { return operationKey; }
    public String getPayloadHash() { return payloadHash; }
    public ParkingOperationStatus getStatus() { return status; }
    public String getResponseJson() { return responseJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
