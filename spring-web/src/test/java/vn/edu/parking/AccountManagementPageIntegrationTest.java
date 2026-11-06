package vn.edu.parking;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.GateShiftAssignment;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.repository.GateShiftAssignmentRepository;
import vn.edu.parking.repository.SecurityAuditRepository;
import vn.edu.parking.repository.SecurityRoleRepository;
import vn.edu.parking.repository.SystemAccountRepository;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:account-page-test;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "parking.upload-dir=target/test-account-page-uploads"
})
@WithMockUser(username = "manager", roles = "MANAGEMENT")
class AccountManagementPageIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SystemAccountRepository accounts;
    @Autowired SecurityAuditRepository audit;
    @Autowired SecurityRoleRepository roles;
    @Autowired GateShiftAssignmentRepository shifts;

    @BeforeEach
    void clearSecurityData() {
        shifts.deleteAll();
        audit.deleteAll();
        accounts.deleteAll();
    }

    @Test
    void createsAccountAndShowsTemporaryPasswordOnlyAsFlashData() throws Exception {
        String roleId = roles.findByCode("GATE_STAFF").orElseThrow().getId().toString();
        mvc.perform(post("/account-management/create").with(csrf())
                .param("username", "gate-01")
                .param("fullName", "Nguyễn Văn Nhân Viên")
                .param("roleId", roleId)
                .param("phone", "0901234567")
                .param("email", "gate01@example.test")
                .param("citizenId", "079203001234")
                .param("dateOfBirth", "2000-01-15")
                .param("address", "Quận 1, Thành phố Hồ Chí Minh"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/account-management"))
            .andExpect(flash().attributeExists("temporaryPassword"));

        assertTrue(accounts.findByUsername("gate-01").isPresent());
        var account = accounts.findByUsername("gate-01").orElseThrow();
        assertEquals("Nguyễn Văn Nhân Viên", account.getFullName());
        assertTrue(account.getEmployeeCode().matches("GC-[0-9]{3,}"));
        assertEquals("0901234567", account.getPhone());
        assertEquals("gate01@example.test", account.getEmail());
        assertEquals("079203001234", account.getCitizenId());
        assertEquals("GATE_STAFF", account.getSecurityRole().getCode());
        assertTrue(audit.findAll().stream().anyMatch(event -> "ACCOUNT_CREATE".equals(event.getAction())));
    }

    @Test
    void rejectsAccountMutationWithoutCsrf() throws Exception {
        mvc.perform(post("/account-management/create")
                .param("username", "gate-02")
                .param("role", "GATE_STAFF"))
            .andExpect(status().isForbidden());
    }

    @Test
    void rendersSessionAndShiftManagementSections() throws Exception {
        mvc.perform(get("/account-management"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Phiên Desktop được ghi nhận")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Phiên Web được ghi nhận")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Ca làm gần đây")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"app-dialog\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"#live-shifts\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"#live-sessions\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-confirm=")));
    }

    @Test
    void rendersLiveSessionAndShiftFragments() throws Exception {
        var result = mvc.perform(get("/account-management/live")
                .header("X-Requested-With", "XMLHttpRequest"))
            .andExpect(status().isOk()).andReturn();
        String html = result.getResponse().getContentAsString();
        assertTrue(html.contains("id=\"live-sessions\""));
        assertTrue(html.contains("id=\"live-shifts\""));
        assertTrue(html.contains("Phiên Desktop được ghi nhận"));
        assertTrue(html.contains("Dữ liệu đang hiển thị được ghi nhận lúc"),
            "Live data needs a visible successful-snapshot time.");
        assertTrue(html.contains("id=\"live-refresh-retry\""), "Live data needs an explicit manual GET refresh control.");
        assertTrue(html.contains("data-snapshot-at="), "The rendered live fragments need a snapshot timestamp.");
        assertTrue(!html.contains("confirm("));
    }

    @Test
    @WithMockUser(username = "shift-manager", authorities = "SHIFT_MANAGE")
    void shiftManagerReceivesOnlyShiftDataAndUsernameOptions() throws Exception {
        var staff = saveGateStaff("ops-gate-synthetic");
        shifts.saveAndFlush(new GateShiftAssignment(staff, "Ca tổng hợp", Instant.now().plusSeconds(600),
            Instant.now().plusSeconds(3600), "Synthetic shift duty", "Synthetic shift note"));

        MvcResult result = mvc.perform(get("/account-management")
                .flashAttr("temporaryPassword", "SYNTHETIC_TEMPORARY_SECRET"))
            .andExpect(status().isOk())
            .andReturn();

        String html = result.getResponse().getContentAsString();
        assertTrue(html.contains("Ca làm gần đây"));
        assertTrue(html.contains("ops-gate-synthetic"));
        assertTrue(html.contains("Synthetic shift duty"));
        assertFalse(html.contains("PII_MARKER"));
        assertFalse(html.contains("0909876543"));
        assertFalse(html.contains("pii-marker@example.test"));
        assertFalse(html.contains("079203009876"));
        assertFalse(html.contains("1991-02-03"));
        assertFalse(html.contains("Synthetic private address"));
        assertFalse(html.contains("SYNTHETIC_TEMPORARY_SECRET"));
        assertFalse(html.contains("Phiên Desktop được ghi nhận"));
        assertFalse(html.contains("Phiên Web được ghi nhận"));

        Map<String, Object> model = result.getModelAndView().getModel();
        assertFalse(model.containsKey("accounts"));
        assertFalse(model.containsKey("roles"));
        assertFalse(model.containsKey("activeDesktopSessions"));
        assertFalse(model.containsKey("activeWebSessions"));
        assertFalse(model.containsKey("temporaryPassword"));
        assertEquals(List.of("ops-gate-synthetic"), model.get("gateStaff"));
    }

    @Test
    @WithMockUser(username = "security-auditor", authorities = "SECURITY_AUDIT_VIEW")
    void auditViewerGetsNoAccountShiftOrSessionDataFromAccountManagement() throws Exception {
        var staff = saveGateStaff("audit-target-synthetic");
        shifts.saveAndFlush(new GateShiftAssignment(staff, "Audit target shift", Instant.now().plusSeconds(600),
            Instant.now().plusSeconds(3600), "Synthetic audit-target duty", null));

        MvcResult page = mvc.perform(get("/account-management"))
            .andExpect(status().isOk())
            .andReturn();
        String pageHtml = page.getResponse().getContentAsString();
        assertTrue(pageHtml.contains("/security-audit"));
        assertFalse(pageHtml.contains("PII_MARKER"));
        assertFalse(pageHtml.contains("Audit target shift"));
        assertFalse(pageHtml.contains("Synthetic audit-target duty"));
        assertFalse(page.getModelAndView().getModel().containsKey("accounts"));
        assertFalse(page.getModelAndView().getModel().containsKey("gateStaff"));
        assertFalse(page.getModelAndView().getModel().containsKey("shiftAssignments"));

        MvcResult live = mvc.perform(get("/account-management/live")
                .flashAttr("temporaryPassword", "SYNTHETIC_LIVE_TEMPORARY_SECRET"))
            .andExpect(status().isOk())
            .andReturn();
        String liveHtml = live.getResponse().getContentAsString();
        assertFalse(liveHtml.contains("PII_MARKER"));
        assertFalse(liveHtml.contains("Audit target shift"));
        assertFalse(liveHtml.contains("Synthetic audit-target duty"));
        assertFalse(liveHtml.contains("SYNTHETIC_LIVE_TEMPORARY_SECRET"));
        assertFalse(live.getModelAndView().getModel().containsKey("activeDesktopSessions"));
        assertFalse(live.getModelAndView().getModel().containsKey("activeWebSessions"));
        assertFalse(live.getModelAndView().getModel().containsKey("shiftAssignments"));
        assertFalse(live.getModelAndView().getModel().containsKey("temporaryPassword"));

        mvc.perform(get("/security-audit")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "account-manager", authorities = "ACCOUNT_MANAGE")
    void accountManagerGetsProfileFieldsButNotAccountEntitySecretsInTheViewModel() throws Exception {
        saveGateStaff("admin-target-synthetic");

        MvcResult result = mvc.perform(get("/account-management"))
            .andExpect(status().isOk())
            .andReturn();

        String html = result.getResponse().getContentAsString();
        assertTrue(html.contains("PII_MARKER Full Name"));
        assertTrue(html.contains("0909876543"));
        assertTrue(html.contains("pii-marker@example.test"));
        assertTrue(html.contains("079203009876"));
        assertTrue(html.contains("1991-02-03"));
        assertTrue(html.contains("Synthetic private address"));
        assertFalse(html.contains("synthetic-password-hash"));
        assertTrue(html.contains("Phiên Desktop được ghi nhận"));
        assertTrue(html.contains("Phiên Web được ghi nhận"));
        assertTrue(result.getModelAndView().getModel().containsKey("accounts"));
        assertTrue(result.getModelAndView().getModel().containsKey("roles"));
        assertTrue(result.getModelAndView().getModel().containsKey("activeDesktopSessions"));
        assertTrue(result.getModelAndView().getModel().containsKey("activeWebSessions"));
        assertFalse(((List<?>) result.getModelAndView().getModel().get("accounts")).getFirst()
            instanceof SystemAccount, "The template model must not contain persistence entities.");

        mvc.perform(get("/account-admin/accounts"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("079203009876")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("synthetic-password-hash"))));
    }

    @Test
    void shiftRowsDistinguishUpcomingActiveAndEndedAndConfirmTheSpecificAssignment() throws Exception {
        SystemAccount operator = accounts.saveAndFlush(new SystemAccount(
            "shift-qa", "Synthetic operator", "not-a-real-password", AccountRole.MANAGEMENT, false));
        Instant now = Instant.now();
        shifts.save(new GateShiftAssignment(operator, "Ca sắp diễn ra", now.plusSeconds(3600),
            now.plusSeconds(7200), "Synthetic upcoming duty", null));
        shifts.save(new GateShiftAssignment(operator, "Ca đang diễn ra", now.minusSeconds(3600),
            now.plusSeconds(3600), "Synthetic current duty", null));
        shifts.saveAndFlush(new GateShiftAssignment(operator, "Ca đã kết thúc", now.minusSeconds(7200),
            now.minusSeconds(3600), "Synthetic ended duty", null));

        var result = mvc.perform(get("/account-management/live"))
            .andExpect(status().isOk()).andReturn();
        String html = result.getResponse().getContentAsString();
        assertTrue(html.contains("Sắp diễn ra"), "Upcoming shifts need a distinct status label.");
        assertTrue(html.contains("Đang diễn ra"), "Active shifts need a distinct status label.");
        assertTrue(html.contains("Đã kết thúc"), "Ended shifts need a distinct status label.");
        assertTrue(html.contains("data-confirm-title=\"Xóa phân công ca\""));
        assertTrue(html.contains("data-confirm=\"Xóa ca Ca sắp diễn ra của shift-qa, bắt đầu"),
            "Delete confirmation must identify the specific shift and assignee.");
        assertTrue(html.contains("tối đa 50 ca"), "The displayed list cap must be disclosed.");
    }

    @Test
    @WithMockUser(username = "gate", roles = "GATE_STAFF")
    void gateStaffCannotReadLiveManagementData() throws Exception {
        mvc.perform(get("/account-management/live"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gate", roles = "GATE_STAFF")
    void gateStaffCannotRevokeSessions() throws Exception {
        mvc.perform(post("/account-management/sessions/desktop/revoke").with(csrf())
                .param("sessionId", "not-authorized"))
            .andExpect(status().isForbidden());
    }

    private SystemAccount saveGateStaff(String username) {
        SystemAccount account = new SystemAccount(username, "PII_MARKER Full Name", "synthetic-password-hash",
            AccountRole.GATE_STAFF, false);
        account.setSecurityRole(roles.findByCode("GATE_STAFF").orElseThrow());
        account.setEmployeeCode("PII_MARKER-EMP");
        account.setPhone("0909876543");
        account.setEmail("pii-marker@example.test");
        account.setCitizenId("079203009876");
        account.setDateOfBirth(LocalDate.of(1991, 2, 3));
        account.setAddress("Synthetic private address");
        return accounts.saveAndFlush(account);
    }
}
