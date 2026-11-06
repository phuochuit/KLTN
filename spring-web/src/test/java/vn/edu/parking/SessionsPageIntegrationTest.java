package vn.edu.parking;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.parking.domain.ParkingCard;
import vn.edu.parking.domain.ParkingSession;
import vn.edu.parking.domain.SessionStatus;
import vn.edu.parking.repository.ParkingCardRepository;
import vn.edu.parking.repository.ParkingSessionRepository;
import vn.edu.parking.service.AiProxyService;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@WithMockUser(roles = "MANAGEMENT")
class SessionsPageIntegrationTest {
    private String searchPrefix;
    private String cardCode;

    @Autowired MockMvc mvc;
    @Autowired ParkingSessionRepository sessions;
    @Autowired ParkingCardRepository cards;
    @MockitoBean AiProxyService aiProxy;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        LoginTestIsolation.register(registry, "sessions-page-test");
    }

    @BeforeEach
    void seedSessions() {
        String fixtureId = UUID.randomUUID().toString().substring(0, 8);
        searchPrefix = "sqa-" + fixtureId + "-";
        cardCode = "SESSIONS-CARD-" + fixtureId;
        ParkingCard card = new ParkingCard();
        card.setCardCode(cardCode);
        card = cards.saveAndFlush(card);

        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        for (int index = 0; index < 20; index++) {
            ParkingSession session = new ParkingSession();
            session.setEntryPlate(searchPrefix + String.format("%02d", index));
            session.setEntryTime(start.plusMinutes(index));
            session.setFee(BigDecimal.valueOf(index * 1000L));
            session.setStatus(index < 12 ? SessionStatus.OPEN : SessionStatus.COMPLETED);
            if (index == 5) session.setParkingCard(card);
            if (index >= 12) {
                session.setExitPlate("OUTBOUND-SEARCH-QA-" + index);
                session.setExitTime(start.plusHours(1).plusMinutes(index));
            }
            sessions.saveAndFlush(session);
        }
    }

    @Test
    void searchStatusSortAndPaginationKeepTheirUrlState() throws Exception {
        mvc.perform(get("/sessions").param("q", searchPrefix).param("status", "OPEN")
                .param("sort", "oldest").param("page", "1").param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(view().name("sessions"))
            .andExpect(model().attribute("sessionPage", hasProperty("totalElements", equalTo(12L))))
            .andExpect(model().attribute("sessionPage", hasProperty("number", equalTo(1))))
            .andExpect(model().attribute("sessions", hasSize(2)))
            .andExpect(content().string(containsString(searchPrefix + "10")))
            .andExpect(content().string(containsString(searchPrefix + "11")))
            .andExpect(content().string(not(containsString(searchPrefix + "09"))))
            .andExpect(content().string(containsString("name=\"q\"")))
            .andExpect(content().string(containsString("status=OPEN")))
            .andExpect(content().string(containsString("sort=oldest")))
            .andExpect(content().string(containsString("size=10")))
            .andExpect(content().string(containsString("page=0")));

        mvc.perform(get("/sessions").param("q", cardCode))
            .andExpect(status().isOk())
            .andExpect(model().attribute("sessionPage", hasProperty("totalElements", equalTo(1L))))
            .andExpect(content().string(containsString(searchPrefix + "05")));

        mvc.perform(get("/sessions").param("q", "OUTBOUND-SEARCH-QA").param("status", "COMPLETED"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("sessionPage", hasProperty("totalElements", equalTo(8L))))
            .andExpect(content().string(containsString("OUTBOUND-SEARCH-QA-19")))
            .andExpect(content().string(not(containsString(searchPrefix + "11"))));

        mvc.perform(get("/sessions").param("q", "%"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("sessionPage", hasProperty("totalElements", equalTo(0L))))
            .andExpect(content().string(containsString("Không có lượt xe phù hợp")));
    }

    @Test
    void outOfRangePageResolvesToLastAvailablePageAndEmptyResultsResolveToFirst() throws Exception {
        mvc.perform(get("/sessions").param("q", searchPrefix).param("status", "OPEN")
                .param("sort", "oldest").param("size", "10").param("page", "99"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("sessionPage", hasProperty("number", equalTo(1))))
            .andExpect(model().attribute("sessionPage", hasProperty("totalPages", equalTo(2))))
            .andExpect(model().attribute("sessions", hasSize(2)))
            .andExpect(model().attribute("pageNumber", 1))
            .andExpect(model().attribute("selectedStatus", "OPEN"))
            .andExpect(model().attribute("selectedSort", "oldest"))
            .andExpect(model().attribute("firstResult", 11L))
            .andExpect(model().attribute("lastResult", 12L));

        mvc.perform(get("/sessions").param("q", "NO-SESSIONS-MATCH-QA").param("page", "99"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("sessionPage", hasProperty("number", equalTo(0))))
            .andExpect(model().attribute("pageNumber", 0))
            .andExpect(model().attribute("sessions", hasSize(0)));
    }

    @Test
    void entryDateRangeIncludesBothCalendarDaysAndExcludesFollowingMidnight() throws Exception {
        String datePrefix = "date-qa-" + UUID.randomUUID().toString().substring(0, 8);
        saveSession(datePrefix + "-start", LocalDate.of(2025, 5, 17).atStartOfDay());
        saveSession(datePrefix + "-end", LocalDateTime.of(2025, 5, 18, 23, 59, 59));
        saveSession(datePrefix + "-next", LocalDate.of(2025, 5, 19).atStartOfDay());

        mvc.perform(get("/sessions").param("q", datePrefix)
                .param("from", "2025-05-17").param("to", "2025-05-18"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("sessionPage", hasProperty("totalElements", equalTo(2L))))
            .andExpect(model().attribute("sessions", hasSize(2)))
            .andExpect(model().attribute("selectedFrom", "2025-05-17"))
            .andExpect(model().attribute("selectedTo", "2025-05-18"))
            .andExpect(content().string(containsString(datePrefix + "-start")))
            .andExpect(content().string(containsString(datePrefix + "-end")))
            .andExpect(content().string(not(containsString(datePrefix + "-next"))));

        mvc.perform(get("/sessions").param("q", datePrefix).param("to", "2025-05-18"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("sessionPage", hasProperty("totalElements", equalTo(2L))));

        mvc.perform(get("/sessions").param("q", datePrefix).param("from", "2025-05-18"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("sessionPage", hasProperty("totalElements", equalTo(2L))))
            .andExpect(content().string(containsString(datePrefix + "-end")))
            .andExpect(content().string(containsString(datePrefix + "-next")))
            .andExpect(content().string(not(containsString(datePrefix + "-start"))));
    }

    @Test
    void invalidOrReversedEntryDatesDoNotSilentlyShowUnfilteredHistory() throws Exception {
        mvc.perform(get("/sessions").param("q", searchPrefix)
                .param("from", "2025-02-30").param("to", "2025-03-01"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("sessionPage", hasProperty("totalElements", equalTo(0L))))
            .andExpect(content().string(containsString("Khoảng ngày không hợp lệ")))
            .andExpect(content().string(not(containsString("Chưa có lượt xe nào được ghi nhận"))))
            .andExpect(content().string(not(containsString(searchPrefix + "00"))));

        mvc.perform(get("/sessions").param("q", searchPrefix)
                .param("from", "2025-05-18").param("to", "2025-05-17"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("sessionPage", hasProperty("totalElements", equalTo(0L))))
            .andExpect(content().string(containsString("Khoảng ngày không hợp lệ")));
    }

    @Test
    void paginationLinksKeepTheEntryDateRange() throws Exception {
        mvc.perform(get("/sessions").param("q", searchPrefix).param("sort", "oldest")
                .param("size", "10").param("page", "1")
                .param("from", "2024-01-01").param("to", "2024-01-01"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("sessionPage", hasProperty("totalElements", equalTo(20L))))
            .andExpect(content().string(containsString("from=2024-01-01")))
            .andExpect(content().string(containsString("to=2024-01-01")));
    }

    @Test
    void clearingOnlyStatusKeepsOtherHistoryFilters() throws Exception {
        mvc.perform(get("/sessions").param("q", "preserve-qa").param("status", "OPEN")
                .param("from", "2025-05-17").param("to", "2025-05-18")
                .param("sort", "oldest").param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Xem tất cả trạng thái")))
            .andExpect(content().string(containsString(
                "href=\"/sessions?q=preserve-qa&amp;from=2025-05-17&amp;to=2025-05-18&amp;sort=oldest&amp;size=20&amp;page=0\"")));
    }

    @Test
    void marksOnlyNormalizedEntryExitPlateDifferencesWithANeutralLabel() throws Exception {
        String platePrefix = "plate-qa-" + UUID.randomUUID().toString().substring(0, 8);
        ParkingSession samePlate = saveSession(platePrefix + "-same", LocalDateTime.of(2025, 6, 1, 8, 0));
        samePlate.setExitPlate((platePrefix + "-SAME").replace('-', ' '));
        sessions.saveAndFlush(samePlate);
        ParkingSession differentPlate = saveSession(platePrefix + "-different", LocalDateTime.of(2025, 6, 1, 9, 0));
        differentPlate.setExitPlate("DIFFERENT-PLATE");
        sessions.saveAndFlush(differentPlate);

        mvc.perform(get("/sessions").param("q", platePrefix))
            .andExpect(status().isOk())
            .andExpect(model().attribute("plateMismatchSessionIds", hasSize(1)))
            .andExpect(content().string(containsString("Khác biển số vào")));
    }

    private ParkingSession saveSession(String plate, LocalDateTime entryTime) {
        ParkingSession session = new ParkingSession();
        session.setEntryPlate(plate);
        session.setEntryTime(entryTime);
        session.setStatus(SessionStatus.COMPLETED);
        session.setDetectedVehicleType(vn.edu.parking.domain.VehicleType.CAR);
        session.setFee(BigDecimal.ZERO);
        return sessions.saveAndFlush(session);
    }
}
