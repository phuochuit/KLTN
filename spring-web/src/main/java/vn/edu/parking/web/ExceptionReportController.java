package vn.edu.parking.web;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.edu.parking.service.ExceptionReportService;
import vn.edu.parking.web.dto.ExceptionReportPageResponse;

@Controller
public class ExceptionReportController {
    private final ExceptionReportService exceptionReportService;

    public ExceptionReportController(ExceptionReportService exceptionReportService) {
        this.exceptionReportService = exceptionReportService;
    }

    @GetMapping("/exceptions")
    public String showExceptionsPage(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false, defaultValue = "ALL") String category,
            @RequestParam(required = false, defaultValue = "ALL") String severity,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String keyword,
            Model model) {

        LocalDate effectiveToDate = toDate != null ? toDate : LocalDate.now();
        LocalDate effectiveFromDate = fromDate != null ? fromDate : effectiveToDate.minusDays(7);

        Instant fromInstant = effectiveFromDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant toInstant = effectiveToDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusMillis(1);

        ExceptionReportPageResponse response = exceptionReportService.getExceptionReport(
            fromInstant, toInstant, category, severity, operator, keyword);

        model.addAttribute("items", response.items());
        model.addAttribute("summary", response.summary());
        model.addAttribute("totalItems", response.totalItems());
        model.addAttribute("fromDate", effectiveFromDate);
        model.addAttribute("toDate", effectiveToDate);
        model.addAttribute("category", category);
        model.addAttribute("severity", severity);
        model.addAttribute("operator", operator != null ? operator : "");
        model.addAttribute("keyword", keyword != null ? keyword : "");

        return "exceptions";
    }

    @GetMapping("/api/parking/exceptions")
    @ResponseBody
    public ResponseEntity<ExceptionReportPageResponse> getExceptionsApi(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false, defaultValue = "ALL") String category,
            @RequestParam(required = false, defaultValue = "ALL") String severity,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String keyword) {

        ExceptionReportPageResponse response = exceptionReportService.getExceptionReport(
            from, to, category, severity, operator, keyword);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = {"/exceptions/export-csv", "/api/parking/exceptions/export-csv"}, produces = "text/csv; charset=UTF-8")
    @ResponseBody
    public ResponseEntity<byte[]> exportExceptionsCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false, defaultValue = "ALL") String category,
            @RequestParam(required = false, defaultValue = "ALL") String severity,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String keyword) {

        LocalDate effectiveToDate = toDate != null ? toDate : LocalDate.now();
        LocalDate effectiveFromDate = fromDate != null ? fromDate : effectiveToDate.minusDays(7);

        Instant fromInstant = effectiveFromDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant toInstant = effectiveToDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusMillis(1);

        ExceptionReportPageResponse response = exceptionReportService.getExceptionReport(
            fromInstant, toInstant, category, severity, operator, keyword);

        StringBuilder csv = new StringBuilder(CsvUtils.UTF_8_BOM);
        csv.append("Mã sự kiện,Thời gian,Phân loại,Mức độ,Biển số xe,Nhân viên thực hiện,Trạm / Ca trực,Mã lý do,Mô tả chi tiết,Hành động đề xuất\r\n");

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

        for (var item : response.items()) {
            String op = item.operatorFullName() != null && !item.operatorFullName().isBlank()
                ? item.operatorFullName() : (item.operatorUsername() != null ? item.operatorUsername() : "");
            String station = item.shiftName() != null && !item.shiftName().isBlank()
                ? item.shiftName() : (item.operation() != null ? item.operation() : "");
            csv.append(CsvUtils.escape(item.id())).append(",")
               .append(CsvUtils.escape(item.timestamp() != null ? dtf.format(item.timestamp()) : "")).append(",")
               .append(CsvUtils.escape(item.categoryLabel())).append(",")
               .append(CsvUtils.escape(item.severity())).append(",")
               .append(CsvUtils.escape(item.plateNumber())).append(",")
               .append(CsvUtils.escape(op)).append(",")
               .append(CsvUtils.escape(station)).append(",")
               .append(CsvUtils.escape(item.reasonCode())).append(",")
               .append(CsvUtils.escape(item.description())).append(",")
               .append(CsvUtils.escape(item.statusOrOutcome())).append("\r\n");
        }

        csv.append("TỔNG CỘNG,,Tổng số sự kiện: ").append(response.totalItems())
           .append(",,,,,Can thiệp thủ công: ").append(response.summary().manualOverridesCount())
           .append(" | Cảnh báo an ninh: ").append(response.summary().securityAlertsCount())
           .append(" | Lỗi thanh toán: ").append(response.summary().paymentIssuesCount())
           .append(" | Bất thường khuôn mặt: ").append(response.summary().faceRejectsCount())
           .append("\r\n");

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bao_cao_ngoai_le_" + effectiveFromDate + "_" + effectiveToDate + ".csv\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(bytes);
    }

}
