package vn.edu.parking;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.ParkingSessionRepository;
import vn.edu.parking.repository.SystemAccountRepository;
import vn.edu.parking.service.AiProxyService;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class DashboardPageIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SystemAccountRepository accounts;
    @Autowired ParkingSessionRepository sessions;
    @Autowired PasswordEncoder encoder;
    @MockitoBean AiProxyService aiProxy;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        LoginTestIsolation.register(registry, "dashboard-page-test");
    }

    private MockHttpSession login(AccountRole role) throws Exception {
        String username = "dashboard-test-" + UUID.randomUUID();
        accounts.saveAndFlush(new SystemAccount(username, encoder.encode(LoginTestIsolation.PASSWORD), role, false));
        return (MockHttpSession) mvc.perform(post("/login").with(csrf())
            .param("username", username).param("password", LoginTestIsolation.PASSWORD))
            .andExpect(status().is3xxRedirection()).andReturn().getRequest().getSession(false);
    }

    @Test
    void managerRendersScopedDashboardWithNativeCsrfLogoutAndExistingLinks() throws Exception {
        mvc.perform(get("/").session(login(AccountRole.MANAGEMENT)))
            .andExpect(status().isOk()).andExpect(view().name("dashboard"))
            .andExpect(content().string(containsString("/css/dashboard-ui.css")))
            .andExpect(content().string(not(containsString("/css/app.css"))))
            .andExpect(content().string(containsString("name=\"_csrf\"")))
            .andExpect(content().string(containsString("href=\"/roles\"")))
            .andExpect(content().string(containsString("id=\"currentToday\"")));
    }

    @Test
    void gateStaffKeepsDashboardAccessWithoutManagementNavigation() throws Exception {
        var session = login(AccountRole.GATE_STAFF);
        mvc.perform(get("/").session(session)).andExpect(status().isOk())
            .andExpect(content().string(not(containsString("href=\"/roles\""))))
            .andExpect(content().string(not(containsString("href=\"/payments\""))))
            .andExpect(content().string(not(containsString("href=\"/households\""))));
        mvc.perform(get("/roles").session(session)).andExpect(status().isForbidden());
        // Existing policy permits revenue for DASHBOARD_VIEW; redesign does not invent REVENUE_VIEW.
        mvc.perform(get("/api/parking/revenue/monthly").session(session)).andExpect(status().isOk());
    }

    @Test
    void realRevenueFilterAndMonthlyChartAgreeOnKnownCompletedSessionTotals() throws Exception {
        var month = LocalDate.now().withDayOfMonth(10).atTime(12, 0);
        for (int i = 0; i < 2; i++) {
            ParkingSession s = new ParkingSession();
            s.setEntryPlate("SYNTHETIC-" + i); s.setEntryTime(month.minusHours(1));
            s.setExitTime(month); s.setStatus(SessionStatus.COMPLETED);
            s.setFee(BigDecimal.valueOf(i == 0 ? 30000 : 50000)); sessions.saveAndFlush(s);
        }
        var session = login(AccountRole.MANAGEMENT);
        mvc.perform(get("/api/parking/revenue/filter").session(session).param("type", "month")
            .param("year", "" + month.getYear()).param("month", "" + month.getMonthValue()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.visitRevenue").value(80000))
            .andExpect(jsonPath("$.totalRevenue").value(80000)).andExpect(jsonPath("$.sessions", hasSize(2)));
        mvc.perform(get("/api/parking/revenue/monthly").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$[11].totalRevenue").value(80000));
    }

    @Test
    void anonymousCannotOpenDashboardButLocalAssetsRemainAvailable() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection());
        for (String asset : new String[]{"/css/dashboard-ui.css", "/js/dashboard.js", "/js/dashboard-model.mjs", "/images/lucide/layout-dashboard.svg"})
            mvc.perform(get(asset)).andExpect(status().isOk());
    }

    @Test
    void openKpiDestinationActuallyFiltersSessionsAndKeepsHistoryPermission() throws Exception {
        for (SessionStatus status : new SessionStatus[]{SessionStatus.OPEN, SessionStatus.COMPLETED}) {
            ParkingSession s = new ParkingSession(); s.setEntryPlate("KPI-" + status.name());
            s.setEntryTime(LocalDate.now().atTime(10, 0)); s.setStatus(status); sessions.saveAndFlush(s);
        }
        var manager = login(AccountRole.MANAGEMENT);
        mvc.perform(get("/sessions").session(manager).param("status", "OPEN"))
            .andExpect(status().isOk()).andExpect(model().attribute("sessions", hasSize(1)))
            .andExpect(content().string(containsString("KPI-OPEN")))
            .andExpect(content().string(not(containsString("KPI-COMPLETED"))))
            .andExpect(content().string(containsString("Xem tất cả trạng thái")))
            .andExpect(content().string(containsString("data-session-evidence")))
            .andExpect(content().string(containsString("Xem ảnh")))
            .andExpect(content().string(containsString("id=\"evidenceDialog\"")));
        mvc.perform(get("/sessions").session(manager).param("status", "INVALID"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/sessions").session(login(AccountRole.GATE_STAFF)).param("status", "OPEN"))
            .andExpect(status().isForbidden());
    }
}
