package vn.edu.parking.web;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.parking.service.EvidenceComparisonService;
import vn.edu.parking.web.dto.EvidenceComparisonResponse;

@RestController
public class EvidenceComparisonController {
    private final EvidenceComparisonService comparisonService;

    public EvidenceComparisonController(EvidenceComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    @GetMapping("/api/parking/evidence-comparison")
    public EvidenceComparisonResponse compareForDesktop(
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false, defaultValue = "") String plate,
            @RequestParam(required = false, defaultValue = "") String exitVehicleType,
            @RequestParam(required = false, defaultValue = "") String exitVehicleEvidenceId,
            @RequestParam(required = false, defaultValue = "") String exitFaceEvidenceId) {
        return comparisonService.compare(sessionId, plate, exitVehicleType,
            exitVehicleEvidenceId, exitFaceEvidenceId);
    }

    @GetMapping("/admin-data/sessions/{sessionId}/evidence-comparison")
    public EvidenceComparisonResponse compareForAdmin(@PathVariable Long sessionId) {
        return comparisonService.compare(sessionId, null, null, null, null);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}
