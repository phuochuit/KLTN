package vn.edu.parking.service;

import java.time.Duration;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.ParkingOperation;
import vn.edu.parking.domain.ParkingOperationStatus;
import vn.edu.parking.repository.ParkingOperationRepository;

@Service
public class ParkingOperationReservationService {
    private static final Duration STALE_AFTER = Duration.ofMinutes(2);
    private final ParkingOperationRepository operations;

    public ParkingOperationReservationService(ParkingOperationRepository operations) {
        this.operations = operations;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation reserve(String actor, String type, String key, String payloadHash) {
        Instant now = Instant.now();
        var existing = operations.findByActorUsernameAndOperationTypeAndOperationKey(actor, type, key);
        if (existing.isPresent()) {
            ParkingOperation operation = existing.get();
            if (!operation.getPayloadHash().equals(payloadHash))
                throw new IllegalArgumentException(
                    "Mã thao tác đã được dùng cho nội dung khác. Vui lòng tạo mã thao tác mới.");
            if (operation.getStatus() == ParkingOperationStatus.COMPLETED)
                return new Reservation(operation.getId(), operation.getResponseJson());
            if (operation.getUpdatedAt().plus(STALE_AFTER).isAfter(now))
                throw new IllegalStateException(
                    "Yêu cầu đang được xử lý. Vui lòng chờ rồi kiểm tra lại.");
            operation.restart(payloadHash, now);
            operations.save(operation);
            return new Reservation(operation.getId(), null);
        }

        try {
            ParkingOperation operation = operations.saveAndFlush(
                new ParkingOperation(actor, type, key, payloadHash, now));
            return new Reservation(operation.getId(), null);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException(
                "Yêu cầu đang được xử lý ở một trạm khác. Vui lòng chờ rồi kiểm tra lại.", ex);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(long id) {
        operations.findById(id).filter(operation ->
            operation.getStatus() == ParkingOperationStatus.PROCESSING).ifPresent(operations::delete);
    }

    public record Reservation(long id, String cachedResponseJson) {
        public boolean completed() { return cachedResponseJson != null; }
    }
}
