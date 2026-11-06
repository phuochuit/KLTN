package vn.edu.parking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.*;
import vn.edu.parking.service.AiProxyService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class DashboardQueryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SystemAccountRepository accounts;
    @Autowired ParkingSessionRepository sessions;
    @Autowired ParkingCardRepository cards;
    @Autowired SubscriptionPaymentRepository payments;
    @Autowired HouseholdRepository households;
    @Autowired VehicleRepository vehicles;
    @Autowired PasswordEncoder encoder;
    @MockitoBean AiProxyService aiProxy;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        LoginTestIsolation.register(registry, "dashboard-query-test");
    }

    @Test
    void summaryCombinesBothSourcesAndExcludesNextPeriodBoundaryAndOpenSessions() throws Exception {
        visit("TEST-VISIT", "2026-05-01T00:00:00", 12000);
        visit("TEST-VISIT-2", "2026-05-31T23:59:59", 8000);
        subscription("TEST-PASS", "2026-05-10T10:00:00", 300000);
        visit("NEXT", "2026-06-01T00:00:00", 90000);
        subscription("NEXT-PASS", "2026-06-01T00:00:00", 400000);
        ParkingSession open = visit("OPEN", "2026-05-12T10:00:00", 50000);
        open.setStatus(SessionStatus.OPEN);
        sessions.saveAndFlush(open);

        mvc.perform(month("summary")).andExpect(status().isOk())
            .andExpect(jsonPath("$.visitRevenue").value(20000))
            .andExpect(jsonPath("$.subscriptionRevenue").value(300000))
            .andExpect(jsonPath("$.totalRevenue").value(320000))
            .andExpect(jsonPath("$.label").value("Tháng 5/2026"))
            .andExpect(jsonPath("$.periodFrom").value(startsWith("2026-05-01T00:00")))
            .andExpect(jsonPath("$.periodTo").value(startsWith("2026-06-01T00:00")))
            .andExpect(jsonPath("$.generatedAt").isNotEmpty());
    }

    @Test
    void trendAgreesWithSummaryAndTransactionsAtCurrentMonthMidnightBoundary() throws Exception {
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        String boundary = currentMonth.atStartOfDay().toString();
        visit("MIDNIGHT-VISIT", boundary, 4000);
        subscription("MIDNIGHT-PASS", boundary, 30000);

        JsonNode trend = body(revenue("trend"));
        assertThat(trend.isArray()).isTrue();
        assertThat(trend.size()).isEqualTo(12);
        for (int i = 0; i < 12; i++) {
            LocalDate month = currentMonth.minusMonths(11 - i);
            JsonNode point = trend.get(i);
            assertThat(point.path("year").asInt()).isEqualTo(month.getYear());
            assertThat(point.path("month").asInt()).isEqualTo(month.getMonthValue());
            assertThat(point.path("label").asText()).isEqualTo(month.getMonthValue() + "/" + month.getYear());
        }
        for (int i = 10; i < 12; i++) {
            LocalDate month = currentMonth.minusMonths(11 - i);
            String year = Integer.toString(month.getYear());
            String monthNumber = Integer.toString(month.getMonthValue());
            JsonNode summary = body(revenue("summary").param("type", "month").param("year", year).param("month", monthNumber));
            JsonNode transactions = body(revenue("transactions").param("type", "month").param("year", year).param("month", monthNumber));
            int visitRevenue = i == 11 ? 4000 : 0;
            int subscriptionRevenue = i == 11 ? 30000 : 0;
            for (JsonNode totals : new JsonNode[] { trend.get(i), summary }) {
                assertThat(totals.path("visitRevenue").decimalValue()).isEqualByComparingTo(Integer.toString(visitRevenue));
                assertThat(totals.path("subscriptionRevenue").decimalValue()).isEqualByComparingTo(Integer.toString(subscriptionRevenue));
                assertThat(totals.path("totalRevenue").decimalValue()).isEqualByComparingTo(Integer.toString(visitRevenue + subscriptionRevenue));
            }
            assertThat(transactions.path("totalItems").asInt()).isEqualTo(i == 11 ? 2 : 0);
            assertThat(transactions.path("items").size()).isEqualTo(i == 11 ? 2 : 0);
            assertThat(transactions.path("filteredRevenue").decimalValue())
                .isEqualByComparingTo(summary.path("totalRevenue").decimalValue());
        }
    }

    @Test
    void yearAndInclusiveCalendarRangeUseTheSameHalfOpenAccountingBasis() throws Exception {
        visit("LEAP", "2024-02-29T00:00:00", 7000);
        subscription("LEAP-PASS", "2024-02-29T23:59:59", 30000);
        visit("NEXT-DAY", "2024-03-01T00:00:00", 11000);
        visit("NEXT-YEAR", "2025-01-01T00:00:00", 99000);
        mvc.perform(revenue("summary").param("type", "range")
                .param("from", "2024-02-29").param("to", "2024-02-29"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalRevenue").value(37000))
            .andExpect(jsonPath("$.periodTo").value(startsWith("2024-03-01T00:00")));
        mvc.perform(revenue("summary").param("type", "year").param("year", "2024"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalRevenue").value(48000));
    }

    @Test
    void transactionsPageAcrossUnionWithStableTimestampSourceAndIdOrdering() throws Exception {
        for (int i = 0; i < 6; i++) {
            visit("VISIT-" + i, "2026-05-10T12:00:00", 1000);
            subscription("PASS-" + i, "2026-05-10T12:00:00", 2000);
        }
        JsonNode first = body(month("transactions").param("size", "10"));
        JsonNode second = body(month("transactions").param("size", "10").param("page", "1"));
        JsonNode oldest = body(month("transactions").param("size", "20").param("sort", "oldest"));
        assertThat(first.path("items").size()).isEqualTo(10);
        assertThat(second.path("items").size()).isEqualTo(2);
        assertThat(first.path("totalItems").asInt()).isEqualTo(12);
        assertThat(first.path("totalPages").asInt()).isEqualTo(2);
        assertThat(first.path("page").asInt()).isZero();
        assertThat(second.path("page").asInt()).isEqualTo(1);
        assertThat(first.path("filteredRevenue").decimalValue()).isEqualByComparingTo("18000");
        Set<String> identities = new HashSet<>();
        for (JsonNode page : new JsonNode[] { first, second }) {
            for (JsonNode item : page.path("items")) {
                assertThat(identities.add(item.path("source").asText() + ":" + item.path("id").asLong())).isTrue();
                assertThat(item.size()).isEqualTo(5);
            }
        }
        assertThat(first.path("items").get(0)).isEqualTo(oldest.path("items").get(11));
        assertThat(second.path("items").get(1)).isEqualTo(oldest.path("items").get(0));
        for (int i = 1; i < oldest.path("items").size(); i++) {
            JsonNode previous = oldest.path("items").get(i - 1);
            JsonNode current = oldest.path("items").get(i);
            int sourceOrder = previous.path("source").asText().compareTo(current.path("source").asText());
            assertThat(sourceOrder).isLessThanOrEqualTo(0);
            if (sourceOrder == 0) assertThat(previous.path("id").asLong()).isLessThan(current.path("id").asLong());
        }
    }

    @Test
    void transactionsUseExitAndPaidTimesForPeriodAndChronologicalOrder() throws Exception {
        ParkingSession crossing = visit("CROSSING", "2026-05-01T00:00:00", 4000);
        crossing.setEntryTime(LocalDateTime.parse("2026-04-30T20:00:00"));
        sessions.saveAndFlush(crossing);
        subscription("MIDDLE", "2026-05-15T12:00:00", 30000);
        visit("LATEST", "2026-05-31T23:59:59", 5000);
        visit("EXCLUDED", "2026-06-01T00:00:00", 90000);
        mvc.perform(month("transactions")).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(3))
            .andExpect(jsonPath("$.filteredRevenue").value(39000))
            .andExpect(jsonPath("$.items[0].reference").value("LATEST"))
            .andExpect(jsonPath("$.items[1].reference").value("MIDDLE"))
            .andExpect(jsonPath("$.items[2].reference").value("CROSSING"));
        mvc.perform(month("transactions").param("sort", "oldest")).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].reference").value("CROSSING"))
            .andExpect(jsonPath("$.items[2].reference").value("LATEST"));
    }

    @Test
    void searchIsCaseInsensitiveAndTreatsLikeWildcardsAndQuotesAsLiterals() throws Exception {
        visit("TEST%_!A", "2026-05-10T12:00:00", 4000);
        visit("TEST-anything", "2026-05-10T13:00:00", 5000);
        subscription("PASS%_!A", "2026-05-10T14:00:00", 30000);
        mvc.perform(month("transactions").param("q", "%_!a").param("source", "ALL"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(2))
            .andExpect(jsonPath("$.filteredRevenue").value(34000));
        mvc.perform(month("transactions").param("q", "%_!a").param("source", "VISIT"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.filteredRevenue").value(4000));
        mvc.perform(month("transactions").param("source", "SUBSCRIPTION"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(month("transactions").param("q", "' OR 1=1 --"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(month("summary")).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRevenue").value(39000));
    }

    @Test
    void summaryRemainsGlobalWhileFilteredRevenueAndCountSpanAllFilteredPages() throws Exception {
        for (int i = 0; i < 12; i++) visit("MATCH-" + i, "2026-05-10T12:00:00", 1000);
        subscription("OTHER", "2026-05-10T12:00:00", 30000);
        mvc.perform(month("transactions").param("q", "match").param("page", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.totalItems").value(12))
            .andExpect(jsonPath("$.filteredRevenue").value(12000));
        mvc.perform(month("summary")).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRevenue").value(42000));
    }

    @Test
    void emptyAndOutOfRangePagesKeepZeroOrGlobalFilteredTotals() throws Exception {
        mvc.perform(month("summary")).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRevenue").value(0));
        mvc.perform(month("transactions")).andExpect(status().isOk())
            .andExpect(jsonPath("$.items", empty())).andExpect(jsonPath("$.totalPages").value(0))
            .andExpect(jsonPath("$.totalItems").value(0)).andExpect(jsonPath("$.filteredRevenue").value(0));
        visit("ONE", "2026-05-10T12:00:00", 5000);
        mvc.perform(month("transactions").param("page", "10").param("size", "50"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items", empty()))
            .andExpect(jsonPath("$.totalPages").value(1)).andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.filteredRevenue").value(5000));
    }

    @Test
    void overviewUsesCurrentKpisAndOnlyEightMinimalRecentSessions() throws Exception {
        LocalDate today = LocalDate.now();
        long initialHouseholdCount = households.count();
        long initialVehicleCount = vehicles.count();
        Household household = new Household();
        household.setHouseholdCode("TEST-HOUSEHOLD");
        household.setApartmentNumber("TEST-APT");
        households.saveAndFlush(household);
        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber("TEST-VEHICLE");
        vehicle.setOwnerName("Synthetic private owner");
        vehicle.setOwnerPhone("private-phone");
        vehicle.setHousehold(household);
        vehicles.saveAndFlush(vehicle);
        for (int i = 0; i < 10; i++) {
            ParkingSession session = visit("RECENT-" + i, today.atStartOfDay().plusMinutes(i).toString(), 1000);
            session.setVehicle(vehicle);
            sessions.saveAndFlush(session);
        }
        ParkingSession open = visit("OPEN", today.atStartOfDay().toString(), 90000);
        open.setStatus(SessionStatus.OPEN);
        open.setExitTime(null);
        sessions.saveAndFlush(open);
        subscription("CURRENT-PASS", today.atStartOfDay().toString(), 30000);
        mvc.perform(get("/api/dashboard/overview").with(user("viewer").authorities(() -> "DASHBOARD_VIEW")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.householdCount").value(initialHouseholdCount + 1))
            .andExpect(jsonPath("$.vehicleCount").value(initialVehicleCount + 1)).andExpect(jsonPath("$.openCount").value(1))
            .andExpect(jsonPath("$.todayRevenue").value(40000)).andExpect(jsonPath("$.monthRevenue").value(40000))
            .andExpect(jsonPath("$.recentSessions", hasSize(8)))
            .andExpect(jsonPath("$.recentSessions[0].entryPlate").value("RECENT-9"))
            .andExpect(jsonPath("$.recentSessions[0].vehicle").doesNotExist())
            .andExpect(jsonPath("$.recentSessions[0].entryMember").doesNotExist())
            .andExpect(jsonPath("$.recentSessions[0].entryFaceImagePath").doesNotExist())
            .andExpect(jsonPath("$.generatedAt").isNotEmpty())
            .andExpect(content().string(not(containsString("private-phone"))));
    }

    @Test
    void invalidPeriodsAlwaysReturnGeneric400WithoutInternalDetails() throws Exception {
        String[][] invalid = {
            { "type", "bad" }, { "year", "nope" }, { "year", "0" }, { "year", "10000" },
            { "month", "0" }, { "month", "13" }
        };
        for (String[] parameter : invalid) {
            bad(revenue("summary").param("type", parameter[0].equals("type") ? parameter[1] : "month")
                .param("year", parameter[0].equals("year") ? parameter[1] : "2026")
                .param("month", parameter[0].equals("month") ? parameter[1] : "5"));
        }
        bad(revenue("summary"));
        bad(revenue("summary").param("type", "month").param("year", "2026"));
        bad(revenue("summary").param("type", "range").param("from", "2026-02-30").param("to", "2026-03-01"));
        bad(revenue("summary").param("type", "range").param("from", "2026-05-02").param("to", "2026-05-01"));
        bad(revenue("summary").param("type", "range").param("from", "2026-05-01"));
    }

    @Test
    void invalidTransactionFiltersAlwaysReturnGeneric400() throws Exception {
        for (String[] parameter : new String[][] {
                { "page", "-1" }, { "page", "abc" }, { "page", "2147483648" },
                { "size", "1" }, { "size", "100" }, { "size", "abc" },
                { "source", "OTHER" }, { "sort", "amount" }, { "q", "x".repeat(81) } }) {
            bad(month("transactions").param(parameter[0], parameter[1]));
        }
    }

    @Test
    void sessionAndAuthorityGuardsPreserveExistingPolicy() throws Exception {
        for (String route : new String[] { "/api/dashboard/overview", "/api/parking/revenue/trend", "/api/parking/revenue/summary?type=month&year=2026&month=5",
                "/api/parking/revenue/transactions?type=month&year=2026&month=5" }) {
            mvc.perform(get(route)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
            mvc.perform(get(route).with(user("other").authorities(() -> "VEHICLE_MANAGE")))
                .andExpect(status().isForbidden());
            mvc.perform(get(route).with(user("manager").roles("MANAGEMENT"))).andExpect(status().isOk());
        }
        mvc.perform(get("/api/dashboard/overview").with(user("history").authorities(() -> "HISTORY_VIEW")))
            .andExpect(status().isForbidden());
        mvc.perform(revenue("summary").param("type", "year").param("year", "2026")
                .with(user("history").authorities(() -> "HISTORY_VIEW"))).andExpect(status().isOk());
    }

    @Test
    void realAccountSessionWorksAndIsInvalidatedAfterAccountDisable() throws Exception {
        String username = "query-test-" + UUID.randomUUID();
        SystemAccount account = accounts.saveAndFlush(new SystemAccount(username,
            encoder.encode(LoginTestIsolation.PASSWORD), AccountRole.GATE_STAFF, false));
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/login").with(csrf())
                .param("username", username).param("password", LoginTestIsolation.PASSWORD))
            .andExpect(status().is3xxRedirection()).andReturn().getRequest().getSession(false);
        mvc.perform(get("/api/dashboard/overview").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/parking/revenue/summary").session(session)
            .param("type", "month").param("year", "2026").param("month", "5")).andExpect(status().isOk());
        account.setEnabled(false);
        accounts.saveAndFlush(account);
        mvc.perform(get("/api/dashboard/overview").session(session)).andExpect(status().is3xxRedirection());
    }

    private MockHttpServletRequestBuilder revenue(String endpoint) {
        return get("/api/parking/revenue/" + endpoint).with(user("viewer").authorities(() -> "DASHBOARD_VIEW"));
    }

    private MockHttpServletRequestBuilder month(String endpoint) {
        return revenue(endpoint).param("type", "month").param("year", "2026").param("month", "5");
    }

    private JsonNode body(MockHttpServletRequestBuilder request) throws Exception {
        return mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void bad(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Tham số báo cáo không hợp lệ."))
            .andExpect(jsonPath("$.trace").doesNotExist()).andExpect(jsonPath("$.exception").doesNotExist());
    }

    private ParkingSession visit(String reference, String at, int amount) {
        ParkingSession session = new ParkingSession();
        session.setEntryPlate(reference);
        session.setEntryTime(LocalDateTime.parse(at).minusHours(1));
        session.setExitTime(LocalDateTime.parse(at));
        session.setStatus(SessionStatus.COMPLETED);
        session.setFee(BigDecimal.valueOf(amount));
        return sessions.saveAndFlush(session);
    }

    private void subscription(String reference, String at, int amount) {
        ParkingCard card = new ParkingCard();
        card.setCardCode(reference);
        cards.saveAndFlush(card);
        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setParkingCard(card);
        payment.setPaidAt(LocalDateTime.parse(at));
        payment.setPeriodStart(LocalDate.parse(at.substring(0, 10)));
        payment.setPeriodEnd(payment.getPeriodStart().plusMonths(1));
        payment.setAmount(BigDecimal.valueOf(amount));
        payments.saveAndFlush(payment);
    }
}
