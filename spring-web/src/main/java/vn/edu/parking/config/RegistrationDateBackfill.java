package vn.edu.parking.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.ParkingSession;
import vn.edu.parking.domain.SubscriptionPayment;
import vn.edu.parking.domain.Vehicle;
import vn.edu.parking.repository.HouseholdRepository;
import vn.edu.parking.repository.ParkingSessionRepository;
import vn.edu.parking.repository.SubscriptionPaymentRepository;
import vn.edu.parking.repository.VehicleRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class RegistrationDateBackfill implements ApplicationRunner {
    private final VehicleRepository vehicles;
    private final HouseholdRepository households;
    private final ParkingSessionRepository sessions;
    private final SubscriptionPaymentRepository payments;

    public RegistrationDateBackfill(VehicleRepository vehicles, HouseholdRepository households,
            ParkingSessionRepository sessions, SubscriptionPaymentRepository payments) {
        this.vehicles = vehicles;
        this.households = households;
        this.sessions = sessions;
        this.payments = payments;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<Long, LocalDateTime> firstActivity = new HashMap<>();
        for (ParkingSession session : sessions.findAll()) {
            if (session.getVehicle() != null && session.getEntryTime() != null) {
                firstActivity.merge(session.getVehicle().getId(), session.getEntryTime(),
                        (a, b) -> a.isBefore(b) ? a : b);
            }
        }
        for (SubscriptionPayment payment : payments.findAll()) {
            if (payment.getParkingCard() != null && payment.getParkingCard().getVehicle() != null
                    && payment.getPaidAt() != null) {
                firstActivity.merge(payment.getParkingCard().getVehicle().getId(), payment.getPaidAt(),
                        (a, b) -> a.isBefore(b) ? a : b);
            }
        }

        // Dữ liệu cũ không có lịch sử riêng được xem là đã tồn tại từ trước
        // hoạt động đầu tiên của hệ thống, thay vì mang ngày khởi động migration.
        LocalDateTime fallback = firstActivity.values().stream()
                .min(LocalDateTime::compareTo).orElseGet(LocalDateTime::now);
        for (Vehicle vehicle : vehicles.findAll()) {
            if (vehicle.getCreatedAt() == null) {
                vehicle.setCreatedAt(firstActivity.getOrDefault(vehicle.getId(), fallback));
                vehicles.save(vehicle);
            }
        }
        households.findAll().forEach(household -> {
            if (household.getCreatedAt() == null) {
                LocalDateTime firstVehicle = vehicles.findByHouseholdIdAndActiveTrue(household.getId()).stream()
                        .map(Vehicle::getCreatedAt).filter(java.util.Objects::nonNull)
                        .min(LocalDateTime::compareTo).orElse(fallback);
                household.setCreatedAt(firstVehicle);
                households.save(household);
            }
        });
    }
}
