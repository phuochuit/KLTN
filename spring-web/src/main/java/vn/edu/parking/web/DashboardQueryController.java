package vn.edu.parking.web;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.parking.service.DashboardQueryService;
import vn.edu.parking.service.DashboardQueryService.*;

@RestController
public class DashboardQueryController {
    private final DashboardQueryService queries;

    public DashboardQueryController(DashboardQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/api/dashboard/overview")
    public Overview overview() {
        return queries.overview();
    }

    @GetMapping("/api/parking/revenue/trend")
    public List<RevenueTrendPoint> trend() {
        return queries.trend();
    }

    @GetMapping("/api/parking/revenue/summary")
    public RevenueSummary summary(@RequestParam(required = false) String type,
            @RequestParam(required = false) String year, @RequestParam(required = false) String month,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return queries.summary(period(type, year, month, from, to));
    }

    @GetMapping("/api/parking/revenue/transactions")
    public TransactionPage transactions(@RequestParam(required = false) String type,
            @RequestParam(required = false) String year, @RequestParam(required = false) String month,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "ALL") String source,
            @RequestParam(defaultValue = "0") String page, @RequestParam(defaultValue = "10") String size,
            @RequestParam(defaultValue = "newest") String sort) {
        Period period = period(type, year, month, from, to);
        int pageNumber = Integer.parseInt(page);
        int pageSize = Integer.parseInt(size);
        if (pageNumber < 0 || !Set.of(10, 20, 50).contains(pageSize) || q.length() > 80
                || !Set.of("ALL", "VISIT", "SUBSCRIPTION").contains(source)
                || !Set.of("newest", "oldest").contains(sort)) {
            throw new IllegalArgumentException();
        }
        return queries.transactions(period, new TransactionFilter(q.trim(), source, pageNumber, pageSize, sort.equals("oldest")));
    }

    private Period period(String type, String year, String month, String from, String to) {
        if (type == null) throw new IllegalArgumentException();
        LocalDate start;
        LocalDate end;
        String label;
        switch (type) {
            case "month" -> {
                YearMonth selected = YearMonth.of(Integer.parseInt(year), Integer.parseInt(month));
                start = selected.atDay(1);
                end = selected.plusMonths(1).atDay(1);
                label = "Tháng " + selected.getMonthValue() + "/" + selected.getYear();
            }
            case "year" -> {
                start = LocalDate.of(Integer.parseInt(year), 1, 1);
                end = start.plusYears(1);
                label = "Năm " + start.getYear();
            }
            case "range" -> {
                if (from == null || to == null) throw new IllegalArgumentException();
                start = LocalDate.parse(from);
                LocalDate last = LocalDate.parse(to);
                if (last.isBefore(start)) throw new IllegalArgumentException();
                end = last.plusDays(1);
                label = start + " – " + last;
            }
            default -> throw new IllegalArgumentException();
        }
        // Keep timestamp parameters within MySQL DATETIME's supported calendar range.
        if (start.getYear() < 1000 || end.getYear() > 9999) throw new IllegalArgumentException();
        return new Period(start.atStartOfDay(), end.atStartOfDay(), label);
    }

    @ExceptionHandler({IllegalArgumentException.class, DateTimeException.class})
    ResponseEntity<Map<String, String>> badRequest(RuntimeException ignored) {
        return ResponseEntity.badRequest().body(Map.of("message", "Tham số báo cáo không hợp lệ."));
    }
}
