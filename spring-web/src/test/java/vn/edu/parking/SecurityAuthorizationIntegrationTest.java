package vn.edu.parking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:security-auth-test;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "parking.upload-dir=target/test-security-uploads"
})
class SecurityAuthorizationIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void anonymousUserIsRedirectedToLoginForManagementPages() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "gate-01", roles = "GATE_STAFF")
    void gateStaffCannotOpenManagementPagesOrAccountAdministration() throws Exception {
        mvc.perform(get("/")).andExpect(status().isForbidden());
        mvc.perform(get("/account-admin/accounts")).andExpect(status().isForbidden());
        mvc.perform(get("/account-management")).andExpect(status().isForbidden());
        mvc.perform(get("/security-audit")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "manager", roles = "MANAGEMENT")
    void managementCanOpenDashboardAndAccountAdministration() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/account-admin/accounts")).andExpect(status().isOk());
        mvc.perform(get("/account-management")).andExpect(status().isOk());
        mvc.perform(get("/security-audit")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "manager", authorities = "ROLE_PASSWORD_CHANGE_REQUIRED")
    void forcedPasswordChangePrincipalCanOnlyOpenPasswordForm() throws Exception {
        mvc.perform(get("/account/password")).andExpect(status().isOk());
        mvc.perform(get("/")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "viewer", authorities = "DASHBOARD_VIEW")
    void dashboardViewerCanLoadSummaryApiButNotFullSessionHistory() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/api/parking/revenue/monthly")).andExpect(status().isOk());
        mvc.perform(get("/sessions")).andExpect(status().isForbidden());
    }
}
