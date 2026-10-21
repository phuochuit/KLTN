package vn.edu.parking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.parking.repository.SecurityRoleRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:security-role-page-test;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@WithMockUser(username = "manager", roles = "MANAGEMENT")
class SecurityRolePageIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SecurityRoleRepository roles;

    @BeforeEach
    void removeCustomRole() {
        roles.findByCode("ACCOUNTANT").ifPresent(roles::delete);
    }

    @Test
    void rendersFriendlyPermissionManagementPage() throws Exception {
        mvc.perform(get("/roles"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Vai trò và quyền hạn")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Vận hành trạm gác")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Tiền tố mã nhân viên")));
    }

    @Test
    void createsCustomRoleWithImmutableIdentityAndSelectedPermissions() throws Exception {
        mvc.perform(post("/roles/create").with(csrf())
                .param("displayName", "Kế toán")
                .param("code", "accountant")
                .param("employeePrefix", "kt")
                .param("description", "Theo dõi doanh thu")
                .param("permissions", "DASHBOARD_VIEW", "HISTORY_VIEW"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/roles"))
            .andExpect(flash().attribute("message", "Đã tạo vai trò mới."));

        var role = roles.findByCode("ACCOUNTANT").orElseThrow();
        assertEquals("KT", role.getEmployeePrefix());
        assertEquals(2, role.getPermissions().size());
    }

    @Test
    void rejectsDuplicateEmployeePrefix() throws Exception {
        mvc.perform(post("/roles/create").with(csrf())
                .param("displayName", "Vai trò trùng mã")
                .param("code", "ACCOUNTANT")
                .param("employeePrefix", "GC"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("error", "Tiền tố mã nhân viên đã được sử dụng"));
        assertTrue(roles.findByCode("ACCOUNTANT").isEmpty());
    }

    @Test
    void cannotDisableSystemRole() throws Exception {
        var management = roles.findByCode("MANAGEMENT").orElseThrow();
        mvc.perform(post("/roles/update").with(csrf())
                .param("id", management.getId().toString())
                .param("displayName", management.getDisplayName())
                .param("enabled", "false")
                .param("permissions", "ACCOUNT_MANAGE", "ROLE_MANAGE"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("error", "Không thể ngừng sử dụng vai trò hệ thống"));
    }
}
