package vn.edu.parking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.repository.SecurityAuditRepository;
import vn.edu.parking.repository.SystemAccountRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mỗi tài khoản chỉ được đăng nhập Web trên tối đa 2 thiết bị.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:web-session-limit-test;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "parking.upload-dir=target/test-session-limit-uploads"
})
class WebSessionLimitIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SystemAccountRepository accounts;
    @Autowired SecurityAuditRepository audit;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SessionRegistry sessionRegistry;

    @BeforeEach
    void prepareAccount() {
        audit.deleteAll();
        accounts.deleteAll();
        accounts.save(new SystemAccount("manager", passwordEncoder.encode("valid test password"),
            AccountRole.MANAGEMENT, false));
    }

    @Test
    void keepsOnlyTwoWebSessionsWhenThirdDeviceSignsIn() throws Exception {
        login("100.64.0.1", "Chrome Windows");
        login("100.64.0.2", "Chrome Android");
        login("100.64.0.3", "Safari iPhone");

        int sessions = sessionRegistry.getAllPrincipals().stream()
            .flatMap(principal -> sessionRegistry.getAllSessions(principal, false).stream())
            .toList().size();
        assertEquals(2, sessions, "Chỉ được giữ tối đa 2 phiên Web cho mỗi tài khoản");
    }

    @Test
    void expiresTheOldestSessionAndAsksItToSignInAgain() throws Exception {
        MockHttpSession first = login("100.64.1.1", "Chrome Windows");
        login("100.64.1.2", "Chrome Android");
        login("100.64.1.3", "Safari iPhone");

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/")
                .session(first))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?expired"));
    }

    @Test
    void replacesOlderSessionWhenSameAccountUsesSameIp() throws Exception {
        MockHttpSession normalChrome = login("100.64.2.1", "Chrome Windows");
        MockHttpSession incognitoChrome = login("100.64.2.1", "Chrome Windows Incognito");

        mvc.perform(get("/").session(normalChrome))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?expired"));
        mvc.perform(get("/").session(incognitoChrome))
            .andExpect(status().isOk());
    }

    @Test
    void androidChromeCanOpenDashboardImmediatelyAfterLogin() throws Exception {
        MockHttpSession android = login("118.69.71.138",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36");

        mvc.perform(get("/").session(android)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Chrome/130 Mobile"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Dashboard vận hành")));
    }

    @Test
    void redirectsAuthenticatedMobileSessionWhenChromeResubmitsOldLoginForm() throws Exception {
        MockHttpSession android = login("118.69.71.138",
            "Mozilla/5.0 (Linux; Android 14) Chrome/130 Mobile");

        mvc.perform(post("/login").session(android)
                .param("username", "manager")
                .param("password", "valid test password")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Chrome/130 Mobile"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"));
    }

    @Test
    void refreshesLoginPageWithoutMessageInsteadOfReturning403ForExpiredAnonymousForm() throws Exception {
        mvc.perform(post("/login")
                .param("username", "manager")
                .param("password", "valid test password"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    private MockHttpSession login(String ip, String userAgent) throws Exception {
        var result = mvc.perform(post("/login").with(csrf())
                .param("username", "manager")
                .param("password", "valid test password")
                .header("User-Agent", userAgent)
                .with(request -> { request.setRemoteAddr(ip); return request; }))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"))
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
