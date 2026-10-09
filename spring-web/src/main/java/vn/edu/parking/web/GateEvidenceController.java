package vn.edu.parking.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.parking.domain.GateEvidenceKind;
import vn.edu.parking.domain.GateOperationType;
import vn.edu.parking.service.GateEvidenceService;

import java.time.Instant;

@RestController
@RequestMapping("/api/parking")
public class GateEvidenceController {
    private final GateEvidenceService evidence;

    public GateEvidenceController(GateEvidenceService evidence) {
        this.evidence = evidence;
    }

    @GetMapping("/sessions/{sessionId}/operations/{operation}/evidence/{evidenceKind}/{evidenceId}/image")
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGEMENT', 'ROLE_GATE_STAFF')")
    public ResponseEntity<byte[]> image(@PathVariable Long sessionId, @PathVariable GateOperationType operation,
            @PathVariable GateEvidenceKind evidenceKind, @PathVariable String evidenceId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean derived,
            Authentication authentication) {
        GateEvidenceService.EvidenceImage image = evidence.readImage(sessionId, operation, evidenceKind,
            evidenceId, derived, authentication);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(image.mediaType()))
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
            .header("X-Content-Type-Options", "nosniff")
            .body(image.bytes());
    }

    @PostMapping("/sessions/{sessionId}/operations/{operation}/evidence/{id}/preservation")
    @PreAuthorize("hasAuthority('ROLE_MANAGEMENT')")
    public ResponseEntity<Void> preserve(@PathVariable Long sessionId, @PathVariable GateOperationType operation,
            @PathVariable String id,
            @Valid @RequestBody PreservationRequest request, Authentication authentication) {
        evidence.approvePreservation(sessionId, operation, id, request.preserveUntil(), request.reason(), authentication);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @DeleteMapping("/sessions/{sessionId}/operations/{operation}/evidence/{id}/preservation")
    @PreAuthorize("hasAuthority('ROLE_MANAGEMENT')")
    public ResponseEntity<Void> release(@PathVariable Long sessionId, @PathVariable GateOperationType operation,
            @PathVariable String id, Authentication authentication) {
        evidence.releasePreservation(sessionId, operation, id, authentication);
        return ResponseEntity.noContent().build();
    }

    public record PreservationRequest(@NotNull Instant preserveUntil,
            @NotBlank @Size(max = 400) String reason) { }
}
