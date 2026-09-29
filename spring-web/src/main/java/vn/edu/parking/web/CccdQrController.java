package vn.edu.parking.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.parking.service.CccdQrService;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/cccd")
public class CccdQrController {
    private final CccdQrService service;
    public CccdQrController(CccdQrService service) { this.service = service; }

    @PostMapping("/scan-qr")
    public Map<String, Object> scan(@RequestParam("file") MultipartFile file) {
        var value = service.scan(file);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("raw", value.raw()); result.put("citizenId", value.citizenId());
        result.put("oldCitizenId", value.oldCitizenId()); result.put("fullName", value.fullName());
        result.put("dateOfBirth", value.dateOfBirth()); result.put("gender", value.gender());
        result.put("permanentAddress", value.permanentAddress()); result.put("cccdIssueDate", value.issueDate());
        result.put("message", "Đã đọc QR CCCD và tự điền thông tin");
        return result;
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String,String>> error(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}
