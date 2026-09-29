package vn.edu.parking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.parking.domain.SubscriptionPayment;
import java.time.LocalDateTime;
import java.util.List;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {
    List<SubscriptionPayment> findByPaidAtBetween(LocalDateTime from, LocalDateTime to);
    List<SubscriptionPayment> findAllByOrderByPaidAtDesc();
}
