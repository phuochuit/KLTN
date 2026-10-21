package vn.edu.parking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.repository.SecurityAuditRepository;
import vn.edu.parking.repository.SystemAccountRepository;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:database-login-test;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "parking.upload-dir=target/test-login-uploads"
})
class DatabaseLoginIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SystemAccountRepository accounts;
    @Autowired SecurityAuditRepository audit;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void prepareAccount() {
        audit.deleteAll();
        accounts.deleteAll();
        accounts.save(new SystemAccount("manager", passwordEncoder.encode("valid test password"),
            AccountRole.MANAGEMENT, false));
    }

    @Test
    void rendersVietnameseLoginStatusMessages() throws Exception {
        mvc.perform(get("/login").queryParam("error", ""))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Sai tài khoản hoặc mật khẩu")));
        mvc.perform(get("/login").queryParam("passwordChanged", ""))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Đổi mật khẩu thành công")));
    }

    @Test
    void authenticatesAgainstDatabaseAndWritesSuccessAudit() throws Exception {
        mvc.perform(formLogin().user("manager").password("valid test password"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"));

        assertTrue(audit.findAll().stream().anyMatch(event -> "LOGIN_SUCCESS".equals(event.getAction())));
    }

    @Test
    void rejectsWrongPasswordAndWritesFailureAudit() throws Exception {
        mvc.perform(formLogin().user("manager").password("wrong test password"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error"));

        assertTrue(audit.findAll().stream().anyMatch(event -> "LOGIN_FAILURE".equals(event.getAction())));
    }

    @Test
    void invalidatesExistingWebSessionAfterSecurityVersionChanges() throws Exception {
        var login = mvc.perform(formLogin().user("manager").password("valid test password"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        SystemAccount manager = accounts.findByUsername("manager").orElseThrow();
        manager.incrementSecurityVersion();
        accounts.saveAndFlush(manager);

        mvc.perform(get("/").session(session))
            .andExpect(status().is3xxRedirection());
    }
}
