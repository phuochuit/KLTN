package vn.edu.parking.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "parking_cards", uniqueConstraints = @UniqueConstraint(columnNames = "card_code"))
public class ParkingCard {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "card_code", nullable = false, length = 30)
    private String cardCode;
    @ManyToOne(fetch = FetchType.EAGER)
    private Vehicle vehicle;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardStatus status = CardStatus.ACTIVE;
    @Enumerated(EnumType.STRING)
    @Column
    private PassType passType = PassType.PER_VISIT;
    private LocalDate validFrom;
    private LocalDate validUntil;
    @Column(precision = 12, scale = 0)
    private BigDecimal subscriptionFee = BigDecimal.ZERO;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCardCode() { return cardCode; }
    public void setCardCode(String cardCode) { this.cardCode = cardCode; }
    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }
    public CardStatus getStatus() { return status; }
    public void setStatus(CardStatus status) { this.status = status; }
    public PassType getPassType() { return passType == null ? PassType.PER_VISIT : passType; }
    public void setPassType(PassType passType) { this.passType = passType; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
    public BigDecimal getSubscriptionFee() { return subscriptionFee == null ? BigDecimal.ZERO : subscriptionFee; }
    public void setSubscriptionFee(BigDecimal subscriptionFee) { this.subscriptionFee = subscriptionFee; }
    public boolean isMonthlyValid(LocalDate date) {
        return getPassType() == PassType.MONTHLY && status == CardStatus.ACTIVE
            && validFrom != null && validUntil != null
            && !date.isBefore(validFrom) && !date.isAfter(validUntil);
    }
}
