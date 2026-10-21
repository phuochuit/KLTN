package vn.edu.parking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.parking.repository.SecurityAuditRepository;
import vn.edu.parking.repository.SecurityRoleRepository;
import vn.edu.parking.repository.SystemAccountRepository;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
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

    @BeforeEach
    void clearSecurityData() {
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
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Nhân viên đang đăng nhập Desktop")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Tài khoản đang đăng nhập Web")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Phân công ca làm")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"app-dialog\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-confirm=")));
    }

    @Test
    void rendersLiveSessionAndShiftFragments() throws Exception {
        mvc.perform(get("/account-management/live")
                .header("X-Requested-With", "XMLHttpRequest"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"live-sessions\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"live-shifts\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Theo dõi nhân viên đang làm việc")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("confirm("))));
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
}
