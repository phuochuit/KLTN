package vn.edu.parking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.parking.domain.SlotConversionLog;

import java.util.List;

public interface SlotConversionLogRepository extends JpaRepository<SlotConversionLog, Long> {
    List<SlotConversionLog> findByZone_IdOrderByConvertedAtDesc(Long zoneId);
    List<SlotConversionLog> findAllByOrderByConvertedAtDesc();
}
