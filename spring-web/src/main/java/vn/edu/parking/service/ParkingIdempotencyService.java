package vn.edu.parking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import vn.edu.parking.web.dto.ParkingResponse;

@Service
public class ParkingIdempotencyService {
    private final ParkingOperationReservationService reservations;
    private final ParkingOperationExecutionService executions;
    private final ObjectMapper objectMapper;

    public ParkingIdempotencyService(ParkingOperationReservationService reservations,
            ParkingOperationExecutionService executions, ObjectMapper objectMapper) {
        this.reservations = reservations;
        this.executions = executions;
        this.objectMapper = objectMapper;
    }

    public ParkingResponse execute(String operationId, String type, Object payload,
            Supplier<ParkingResponse> action) {
        if (operationId == null || operationId.isBlank()) return action.get();
        String key = normalizedUuid(operationId);
        String hash = payloadHash(payload);
        var reservation = reservations.reserve(currentActor(), type, key, hash);
        if (reservation.completed()) {
            try {
                return objectMapper.readValue(reservation.cachedResponseJson(), ParkingResponse.class);
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Kết quả yêu cầu đã lưu không thể đọc được.", ex);
            }
        }
        try {
            return executions.execute(reservation.id(), hash, action);
        } catch (RuntimeException ex) {
            reservations.release(reservation.id());
            throw ex;
        }
    }

    private String payloadHash(Object payload) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(payload);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Không thể kiểm tra tính toàn vẹn yêu cầu.", ex);
        }
    }

    private static String normalizedUuid(String value) {
        try {
            return UUID.fromString(value.trim()).toString();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Mã thao tác không hợp lệ.");
        }
    }

    private static String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated())
            throw new IllegalStateException("Không xác định được nhân viên thực hiện thao tác.");
        return authentication.getName();
    }
}
