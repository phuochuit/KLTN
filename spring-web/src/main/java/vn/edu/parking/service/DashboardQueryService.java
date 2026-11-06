package vn.edu.parking.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardQueryService {
    // Preserve the dashboard's accounting basis, not the separate payment reconciliation ledger.
    private static final String REVENUE_ROWS = """
        SELECT s.id, 'VISIT' AS source, s.exit_time AS occurred_at,
               s.entry_plate AS reference, s.fee AS amount
        FROM parking_sessions s
        WHERE s.status = 'COMPLETED' AND s.exit_time >= ? AND s.exit_time < ?
        UNION ALL
        SELECT p.id, 'SUBSCRIPTION' AS source, p.paid_at AS occurred_at,
               c.card_code AS reference, p.amount AS amount
        FROM subscription_payments p JOIN parking_cards c ON c.id = p.parking_card_id
        WHERE p.paid_at >= ? AND p.paid_at < ?
        """;

    private final JdbcTemplate jdbc;

    public DashboardQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Overview overview() {
        Instant generatedAt = Instant.now();
        LocalDate today = LocalDate.now();
        Counts counts = jdbc.queryForObject("""
            SELECT (SELECT COUNT(*) FROM households) AS household_count,
                   (SELECT COUNT(*) FROM vehicles) AS vehicle_count,
                   (SELECT COUNT(*) FROM parking_sessions WHERE status = 'OPEN') AS open_count
            """, (rs, row) -> new Counts(rs.getLong("household_count"), rs.getLong("vehicle_count"),
                rs.getLong("open_count")));
        Totals todayTotals = totals(new Period(today.atStartOfDay(), today.plusDays(1).atStartOfDay(), ""));
        LocalDate month = today.withDayOfMonth(1);
        Totals monthTotals = totals(new Period(month.atStartOfDay(), month.plusMonths(1).atStartOfDay(), ""));
        List<RecentSession> recent = jdbc.query("""
            SELECT id, entry_plate, entry_time, exit_time, status, fee
            FROM parking_sessions ORDER BY entry_time DESC, id DESC LIMIT 8
            """, (rs, row) -> {
                Timestamp exit = rs.getTimestamp("exit_time");
                return new RecentSession(rs.getLong("id"), rs.getString("entry_plate"),
                    rs.getTimestamp("entry_time").toLocalDateTime(), exit == null ? null : exit.toLocalDateTime(),
                    rs.getString("status"), rs.getBigDecimal("fee"));
            });
        return new Overview(generatedAt, counts.householdCount(), counts.vehicleCount(), counts.openCount(),
            todayTotals.totalRevenue(), monthTotals.totalRevenue(), recent);
    }

    public RevenueSummary summary(Period period) {
        Instant generatedAt = Instant.now();
        Totals totals = totals(period);
        return new RevenueSummary(totals.visitRevenue(), totals.subscriptionRevenue(), totals.totalRevenue(),
            period.label(), period.from(), period.toExclusive(), generatedAt);
    }

    public List<RevenueTrendPoint> trend() {
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        List<RevenueTrendPoint> result = new ArrayList<>(12);
        for (int i = 11; i >= 0; i--) {
            LocalDate month = currentMonth.minusMonths(i);
            Totals totals = totals(new Period(month.atStartOfDay(), month.plusMonths(1).atStartOfDay(), ""));
            result.add(new RevenueTrendPoint(month.getYear(), month.getMonthValue(),
                month.getMonthValue() + "/" + month.getYear(), totals.visitRevenue(), totals.subscriptionRevenue(),
                totals.totalRevenue()));
        }
        return result;
    }

    public TransactionPage transactions(Period period, TransactionFilter filter) {
        Instant generatedAt = Instant.now();
        List<Object> parameters = periodParameters(period);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (!filter.source().equals("ALL")) {
            where.append(" AND tx.source = ?");
            parameters.add(filter.source());
        }
        if (!filter.query().isBlank()) {
            where.append(" AND LOWER(tx.reference) LIKE ? ESCAPE '!'");
            String literal = filter.query().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
            parameters.add("%" + literal + "%");
        }
        String from = " FROM (" + REVENUE_ROWS + ") tx" + where;
        FilteredTotals totals = jdbc.queryForObject(
            "SELECT COUNT(*) AS total_items, COALESCE(SUM(tx.amount), 0) AS filtered_revenue" + from,
            (rs, row) -> new FilteredTotals(rs.getLong("total_items"), rs.getBigDecimal("filtered_revenue")),
            parameters.toArray());
        String direction = filter.oldest() ? " ASC" : " DESC";
        parameters.add(filter.size());
        parameters.add((long) filter.page() * filter.size());
        List<RevenueTransaction> items = jdbc.query(
            "SELECT tx.id, tx.source, tx.occurred_at, tx.reference, tx.amount" + from
                + " ORDER BY tx.occurred_at" + direction + ", tx.source" + direction + ", tx.id" + direction
                + " LIMIT ? OFFSET ?",
            (rs, row) -> new RevenueTransaction(rs.getLong("id"), rs.getString("source"),
                rs.getTimestamp("occurred_at").toLocalDateTime(), rs.getString("reference"), rs.getBigDecimal("amount")),
            parameters.toArray());
        long totalPages = (totals.totalItems() + filter.size() - 1) / filter.size();
        return new TransactionPage(items, filter.page(), filter.size(), totals.totalItems(), totalPages,
            totals.filteredRevenue(), generatedAt);
    }

    private Totals totals(Period period) {
        return jdbc.queryForObject("""
            SELECT COALESCE(SUM(CASE WHEN tx.source = 'VISIT' THEN tx.amount ELSE 0 END), 0) AS visit_revenue,
                   COALESCE(SUM(CASE WHEN tx.source = 'SUBSCRIPTION' THEN tx.amount ELSE 0 END), 0) AS subscription_revenue
            FROM (
            """ + REVENUE_ROWS + ") tx",
            (rs, row) -> new Totals(rs.getBigDecimal("visit_revenue"), rs.getBigDecimal("subscription_revenue")),
            periodParameters(period).toArray());
    }

    private List<Object> periodParameters(Period period) {
        return new ArrayList<>(List.of(Timestamp.valueOf(period.from()), Timestamp.valueOf(period.toExclusive()),
            Timestamp.valueOf(period.from()), Timestamp.valueOf(period.toExclusive())));
    }

    public record Period(LocalDateTime from, LocalDateTime toExclusive, String label) { }
    public record TransactionFilter(String query, String source, int page, int size, boolean oldest) { }
    public record Overview(Instant generatedAt, long householdCount, long vehicleCount, long openCount,
            BigDecimal todayRevenue, BigDecimal monthRevenue, List<RecentSession> recentSessions) { }
    public record RecentSession(long id, String entryPlate, LocalDateTime entryTime, LocalDateTime exitTime,
            String status, BigDecimal fee) { }
    // periodFrom is inclusive; periodTo is the exclusive boundary, in the server's local calendar.
    public record RevenueSummary(BigDecimal visitRevenue, BigDecimal subscriptionRevenue, BigDecimal totalRevenue,
            String label, LocalDateTime periodFrom, LocalDateTime periodTo, Instant generatedAt) { }
    public record RevenueTrendPoint(int year, int month, String label, BigDecimal visitRevenue,
            BigDecimal subscriptionRevenue, BigDecimal totalRevenue) { }
    public record RevenueTransaction(long id, String source, LocalDateTime occurredAt, String reference, BigDecimal amount) { }
    public record TransactionPage(List<RevenueTransaction> items, int page, int size, long totalItems, long totalPages,
            BigDecimal filteredRevenue, Instant generatedAt) { }
    private record Counts(long householdCount, long vehicleCount, long openCount) { }
    private record FilteredTotals(long totalItems, BigDecimal filteredRevenue) { }
    private record Totals(BigDecimal visitRevenue, BigDecimal subscriptionRevenue) {
        BigDecimal totalRevenue() { return visitRevenue.add(subscriptionRevenue); }
    }
}
