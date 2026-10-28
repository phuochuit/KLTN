package vn.edu.parking;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import vn.edu.parking.repository.ParkingOperationRepository;
import vn.edu.parking.service.ParkingIdempotencyService;
import vn.edu.parking.service.ParkingOperationReservationService;
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
    @Autowired ParkingOperationReservationService reservations;
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

    @Test
    void concurrentReservationAllowsOnlyOneProcessor() throws Exception {
        String operationId = UUID.randomUUID().toString();
        String payloadHash = "a".repeat(64);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var task = (java.util.concurrent.Callable<String>) () -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                try {
                    reservations.reserve("gate-concurrent", "EXIT_CONFIRM", operationId, payloadHash);
                    return "RESERVED";
                } catch (IllegalStateException ex) {
                    return "REJECTED:" + ex.getMessage();
                }
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)))
                .anyMatch(result -> result.equals("RESERVED"))
                .anyMatch(result -> result.startsWith("REJECTED:"));
            assertThat(operations.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private static ParkingResponse response(long sessionId) {
        LocalDateTime entryTime = LocalDateTime.of(2026, 10, 21, 8, 30);
        return new ParkingResponse(sessionId, "59A112345", "Cư dân kiểm thử", "MOTORBIKE",
            "CARD001", "OPEN", entryTime, null, BigDecimal.ZERO,
            "Đã ghi nhận", false, null, null, null);
    }
}
