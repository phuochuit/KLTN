package vn.edu.parking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.OutboxEvent;
import vn.edu.parking.domain.OutboxStatus;
import vn.edu.parking.repository.OutboxEventRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxEventService {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent recordEvent(String aggregateType, String aggregateId, String eventType, Object payload) {
        String json;
        try {
            json = (payload instanceof String s) ? s : objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Không thể serialize payload outbox: " + e.getMessage(), e);
        }

        String sha256 = calculateSha256(json);
        String eventId = "EVT-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();

        OutboxEvent event = new OutboxEvent(eventId, aggregateType, aggregateId, eventType, json, sha256);
        return outboxEventRepository.save(event);
    }

    @Transactional
    public OutboxEvent recordEventStandalone(String aggregateType, String aggregateId, String eventType, Object payload) {
        String json;
        try {
            json = (payload instanceof String s) ? s : objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Không thể serialize payload outbox: " + e.getMessage(), e);
        }

        String sha256 = calculateSha256(json);
        String eventId = "EVT-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();

        OutboxEvent event = new OutboxEvent(eventId, aggregateType, aggregateId, eventType, json, sha256);
        return outboxEventRepository.save(event);
    }

    @Scheduled(fixedDelayString = "${parking.outbox.poll-ms:5000}")
    @Transactional
    public int processDueEvents() {
        Instant now = Instant.now();
        List<OutboxEvent> dueEvents = outboxEventRepository
                .findTop50ByStatusInAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(
                        List.of(OutboxStatus.PENDING, OutboxStatus.FAILED_RETRY), now);

        int processed = 0;
        for (OutboxEvent event : dueEvents) {
            try {
                // Simulate idempotent dispatching / processing
                log.info("Processing OutboxEvent ID={}, Type={}, AggregateId={}",
                        event.getEventId(), event.getEventType(), event.getAggregateId());
                event.setStatus(OutboxStatus.SENT);
                event.setProcessedAt(Instant.now());
                event.setLastError(null);
                outboxEventRepository.save(event);
                processed++;
            } catch (Exception ex) {
                log.warn("Failed processing OutboxEvent {}: {}", event.getEventId(), ex.getMessage());
                event.setAttemptCount(event.getAttemptCount() + 1);
                event.setLastError(ex.getMessage() != null && ex.getMessage().length() > 500
                        ? ex.getMessage().substring(0, 500) : ex.getMessage());
                if (event.getAttemptCount() >= event.getMaxAttempts()) {
                    event.setStatus(OutboxStatus.DEAD_LETTER);
                } else {
                    event.setStatus(OutboxStatus.FAILED_RETRY);
                    long delaySec = Math.min(300L, (1L << event.getAttemptCount()) * 5L);
                    event.setNextRetryAt(Instant.now().plusSeconds(delaySec));
                }
                outboxEventRepository.save(event);
            }
        }
        return processed;
    }

    @Transactional
    public OutboxEvent replayDeadLetter(Long id) {
        OutboxEvent event = outboxEventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy outbox event ID " + id));
        if (event.getStatus() != OutboxStatus.DEAD_LETTER && event.getStatus() != OutboxStatus.FAILED_RETRY) {
            throw new IllegalStateException("Chỉ sự kiện DEAD_LETTER hoặc FAILED_RETRY mới có thể phát lại.");
        }
        event.setStatus(OutboxStatus.PENDING);
        event.setAttemptCount(0);
        event.setNextRetryAt(Instant.now());
        event.setLastError(null);
        return outboxEventRepository.save(event);
    }

    private static String calculateSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
