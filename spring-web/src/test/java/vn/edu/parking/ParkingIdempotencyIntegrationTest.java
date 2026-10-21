package vn.edu.parking;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import vn.edu.parking.repository.ParkingOperationRepository;
import vn.edu.parking.service.ParkingIdempotencyService;
import vn.edu.parking.web.dto.ParkingResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:parking-idempotency-test;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@WithMockUser(username = "gate-idempotency", roles = "MANAGEMENT")
class ParkingIdempotencyIntegrationTest {
    @Autowired ParkingIdempotencyService idempotency;
    @Autowired ParkingOperationRepository operations;

    @BeforeEach
    void clearOperations() {
        operations.deleteAll();
    }

    @Test
    void duplicateRequestReturnsCachedResponseWithoutRunningActionAgain() {
        String operationId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of("operationId", operationId, "plateNumber", "59A112345");
        AtomicInteger actionCalls = new AtomicInteger();

        ParkingResponse first = idempotency.execute(operationId, "ENTRY", payload,
            () -> response(actionCalls.incrementAndGet()));
        ParkingResponse replay = idempotency.execute(operationId, "ENTRY", payload,
            () -> response(actionCalls.incrementAndGet()));

        assertThat(actionCalls).hasValue(1);
        assertThat(replay).isEqualTo(first);
        assertThat(operations.count()).isEqualTo(1);
    }

    @Test
    void rejectsSameOperationIdWithDifferentPayload() {
        String operationId = UUID.randomUUID().toString();
        idempotency.execute(operationId, "ENTRY",
            Map.of("operationId", operationId, "plateNumber", "59A112345"), () -> response(1));

        assertThatThrownBy(() -> idempotency.execute(operationId, "ENTRY",
                Map.of("operationId", operationId, "plateNumber", "51H88888"), () -> response(2)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("nội dung khác");
    }

    @Test
    void failedActionReleasesReservationSoRequestCanRetry() {
        String operationId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of("operationId", operationId, "plateNumber", "59A112345");

        assertThatThrownBy(() -> idempotency.execute(operationId, "EXIT_CONFIRM", payload,
                () -> { throw new IllegalStateException("Lỗi nghiệp vụ kiểm thử"); }))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Lỗi nghiệp vụ kiểm thử");
        assertThat(operations.count()).isZero();

        ParkingResponse retried = idempotency.execute(operationId, "EXIT_CONFIRM", payload,
            () -> response(7));
        assertThat(retried.sessionId()).isEqualTo(7L);
    }

    @Test
    void rejectsMalformedOperationIdBeforeRunningAction() {
        AtomicInteger actionCalls = new AtomicInteger();

        assertThatThrownBy(() -> idempotency.execute("not-a-uuid", "ENTRY", Map.of(),
                () -> response(actionCalls.incrementAndGet())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Mã thao tác không hợp lệ.");
        assertThat(actionCalls).hasValue(0);
    }

    private static ParkingResponse response(long sessionId) {
        LocalDateTime entryTime = LocalDateTime.of(2026, 10, 21, 8, 30);
        return new ParkingResponse(sessionId, "59A112345", "Cư dân kiểm thử", "MOTORBIKE",
            "CARD001", "OPEN", entryTime, null, BigDecimal.ZERO,
            "Đã ghi nhận", false, null, null, null);
    }
}
