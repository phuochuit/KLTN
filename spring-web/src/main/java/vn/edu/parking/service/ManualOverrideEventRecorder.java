package vn.edu.parking.service;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.*;

@Service
public class ManualOverrideEventRecorder {
    private final ManualOverrideEventRepository events;
    private final SystemAccountRepository accounts;
    private final DesktopSessionRepository desktopSessions;
    private final GateShiftAssignmentRepository shifts;
    private final GateEvidenceRepository evidences;
    private final SecurityAuditService audit;

    public ManualOverrideEventRecorder(ManualOverrideEventRepository events,
            SystemAccountRepository accounts, DesktopSessionRepository desktopSessions,
            GateShiftAssignmentRepository shifts, GateEvidenceRepository evidences,
            SecurityAuditService audit) {
        this.events = events;
        this.accounts = accounts;
        this.desktopSessions = desktopSessions;
        this.shifts = shifts;
        this.evidences = evidences;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rejected(EvidenceOperation operation, String reasonCode, String reasonText,
            Long accountId, String desktopSessionId, Long shiftId,
            String vehicleEvidenceId, String faceEvidenceId, String rejectionReason) {
        var event = new ManualOverrideEvent(operation, ManualOverrideOutcome.REJECTED,
            safeCode(reasonCode), safeText(reasonText, rejectionReason),
            accounts.getReferenceById(accountId), desktopSessions.getReferenceById(desktopSessionId),
            shiftId == null ? null : shifts.getReferenceById(shiftId), null,
            reference(evidences, vehicleEvidenceId), reference(evidences, faceEvidenceId), Instant.now());
        event = events.save(event);
        audit.recordCurrent("MANUAL_OVERRIDE_REJECTED", "MANUAL_OVERRIDE_EVENT",
            event.getId() == null ? null : event.getId().toString(), "FAILURE", rejectionReason,
            faceEvidenceId != null ? faceEvidenceId : vehicleEvidenceId);
    }

    private static GateEvidence reference(GateEvidenceRepository repository, String id) {
        return id == null || id.isBlank() ? null : repository.getReferenceById(id);
    }

    private static String safeCode(String value) {
        String normalized = value == null ? "INVALID_REQUEST" : value.trim().toUpperCase();
        return normalized.isBlank() ? "INVALID_REQUEST" : normalized.substring(0, Math.min(50, normalized.length()));
    }

    private static String safeText(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) normalized = fallback;
        return normalized.substring(0, Math.min(500, normalized.length()));
    }
}
