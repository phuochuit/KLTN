package vn.edu.parking.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.parking.service.CccdQrService;
import vn.edu.parking.service.SecurityAuditService;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/cccd")
public class CccdQrController {
    private final CccdQrService service;
    private final SecurityAuditService audit;
    public CccdQrController(CccdQrService service, SecurityAuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @PostMapping("/scan-qr")
    public Map<String, Object> scan(@RequestParam("file") MultipartFile file) {
        CccdQrService.CccdQrResult value;
        try {
            value = service.scan(file);
        } catch (IllegalArgumentException ex) {
            audit.recordCurrent("CCCD_QR_SCAN", "CCCD_QR", null, "FAILURE", "INVALID_INPUT", null);
            throw ex;
        } catch (IllegalStateException ex) {
            audit.recordCurrent("CCCD_QR_SCAN", "CCCD_QR", null, "FAILURE", "SCAN_ERROR", null);
            throw ex;
        }
        audit.recordCurrent("CCCD_QR_SCAN", "CCCD_QR", null, "SUCCESS", null, null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("citizenId", value.citizenId());
        result.put("oldCitizenId", value.oldCitizenId()); result.put("fullName", value.fullName());
        result.put("dateOfBirth", value.dateOfBirth()); result.put("gender", value.gender());
        result.put("permanentAddress", value.permanentAddress()); result.put("cccdIssueDate", value.issueDate());
        result.put("message", "Đã đọc QR CCCD và tự điền thông tin");
        return result;
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String,String>> error(RuntimeException ex) {
        String message = ex instanceof IllegalStateException ? "Không thể đọc ảnh QR CCCD" : ex.getMessage();
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}
