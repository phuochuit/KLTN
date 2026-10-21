package vn.edu.parking.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.parking.domain.PaymentStatus;
import vn.edu.parking.domain.PaymentTransaction;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    boolean existsByParkingSession_Id(Long parkingSessionId);
    boolean existsBySubscriptionPayment_Id(Long subscriptionPaymentId);

    @EntityGraph(attributePaths = { "parkingSession", "subscriptionPayment", "collectedBy",
        "gateShiftAssignment", "reconciledBy" })
    List<PaymentTransaction> findTop200ByOrderByPaidAtDesc();

    @EntityGraph(attributePaths = { "parkingSession", "subscriptionPayment", "collectedBy",
        "gateShiftAssignment", "reconciledBy" })
    Optional<PaymentTransaction> findDetailedById(Long id);

    @EntityGraph(attributePaths = { "parkingSession", "subscriptionPayment", "collectedBy",
        "gateShiftAssignment", "reconciledBy" })
    List<PaymentTransaction> findByStatusAndPaidAtBetweenOrderByPaidAtDesc(
        PaymentStatus status, Instant from, Instant to);

    @EntityGraph(attributePaths = { "parkingSession", "subscriptionPayment", "collectedBy",
        "gateShiftAssignment", "reconciledBy" })
    List<PaymentTransaction> findByGateShiftAssignment_IdOrderByPaidAtAsc(Long shiftId);
}
