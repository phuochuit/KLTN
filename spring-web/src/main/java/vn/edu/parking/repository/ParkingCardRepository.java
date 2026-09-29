package vn.edu.parking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.parking.domain.ParkingCard;
import java.util.Optional;

public interface ParkingCardRepository extends JpaRepository<ParkingCard, Long> {
    Optional<ParkingCard> findByCardCodeIgnoreCase(String cardCode);
}
