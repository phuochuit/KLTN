package vn.edu.parking;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.repository.SecurityAuditRepository;
import vn.edu.parking.repository.SystemAccountRepository;
import vn.edu.parking.service.AiProxyService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LoginPageIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SystemAccountRepository accounts;
    @Autowired SecurityAuditRepository audit;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoBean AiProxyService aiProxy;
    private String username;

    @DynamicPropertySource
    static void isolatedProperties(DynamicPropertyRegistry registry) throws IOException {
        LoginTestIsolation.register(registry, "login-page-test");
    }

    @BeforeEach
    void seedSyntheticAccount() {
        username = "login-test-" + UUID.randomUUID();
        accounts.saveAndFlush(new SystemAccount(username, passwordEncoder.encode(LoginTestIsolation.PASSWORD),
            AccountRole.MANAGEMENT, false));
    }

    @Test
    void preservesNativeFormBindingsLabelsAndRenderedCsrf() throws Exception {
        MvcResult page = loginPage();
        String html = html(page);
        String form = tagWithAttribute(html, "form", "action", "/login");
        assertEquals("post", attribute(form, "method").toLowerCase());
        assertField(html, "username", "text", "username", "80");
        assertField(html, "password", "password", "current-password", "1024");
        assertEquals("button", attribute(tagWithAttribute(html, "button", "id", "togglePassword"), "type"));
        tagWithAttribute(html, "button", "type", "submit");

        CsrfToken token = (CsrfToken) page.getRequest().getAttribute("_csrf");
        assertNotNull(token, "Login must expose a CSRF token");
        String hidden = tagWithAttribute(html, "input", "name", token.getParameterName());
        assertEquals("hidden", attribute(hidden, "type"));
        assertTrue(token.getToken().equals(attribute(hidden, "value")), "Rendered CSRF must match the request token");
        assertFalse(html.contains("th:action"), "Thymeleaf must render the native action");
    }

    @Test
    void rendersAllExistingVietnameseStatusMessagesWithoutEchoingQueryValues() throws Exception {
        assertMessage("error", "Sai tài khoản hoặc mật khẩu.", "alert");
        assertMessage("throttled", "Đăng nhập sai quá nhiều lần. Vui lòng thử lại sau 15 phút.", "alert");
        assertMessage("logout", "Bạn đã đăng xuất an toàn.", "status");
        assertMessage("passwordChanged", "Đổi mật khẩu thành công. Vui lòng đăng nhập bằng mật khẩu mới.", "status");
    }

    @Test
    void servesReferencedLoginAssetsWithoutAuthenticationOrRemoteDependencies() throws Exception {
        String html = html(loginPage());
        var tags = Pattern.compile("<(?:link|script|img)\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
        while (tags.find()) {
            String tag = tags.group();
            String asset = attribute(tag, tag.toLowerCase().startsWith("<link") ? "href" : "src");
            if (asset.isEmpty()) continue;
            assertTrue(asset.startsWith("/") && !asset.startsWith("//"), "Login assets must be local");
            var response = mvc.perform(get(asset)).andExpect(status().isOk()).andReturn().getResponse();
            assertTrue(response.getContentAsByteArray().length > 0, "Login asset must not be empty");
        }
    }

    @Test
    void rejectsNativeLoginWithoutCsrfOrWithInvalidCsrf() throws Exception {
        mvc.perform(post("/login").param("username", username).param("password", LoginTestIsolation.PASSWORD))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
        mvc.perform(post("/login").with(csrf().useInvalidToken())
                .param("username", username).param("password", LoginTestIsolation.PASSWORD))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
        assertFalse(hasAudit("LOGIN_SUCCESS"), "Rejected CSRF must not authenticate");
    }

    @Test
    void rejectsWrongPasswordThroughTheRealSecurityChain() throws Exception {
        submitLogin(username, "incorrect-test-only-password")
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?error"));
        assertTrue(hasAudit("LOGIN_FAILURE"));
    }

    @Test
    void authenticatesWithTheRenderedNativeFormAndReachesTheDashboard() throws Exception {
        MvcResult login = submitLogin(username, LoginTestIsolation.PASSWORD)
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/")).andReturn();
        MockHttpSession session = session(login);
        SecurityContext context = (SecurityContext) session.getAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertNotNull(context);
        assertTrue(context.getAuthentication().isAuthenticated());
        assertEquals(username, context.getAuthentication().getName());
        mvc.perform(get("/").session(session)).andExpect(status().isOk());
        assertTrue(hasAudit("LOGIN_SUCCESS"));
    }

    @Test
    void forcedPasswordChangeCannotEnterTheDashboard() throws Exception {
        SystemAccount account = accounts.findByUsername(username).orElseThrow();
        account.setMustChangePassword(true);
        accounts.saveAndFlush(account);
        MvcResult login = submitLogin(username, LoginTestIsolation.PASSWORD)
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/account/password")).andReturn();
        MockHttpSession session = session(login);
        mvc.perform(get("/account/password").session(session)).andExpect(status().isOk());
        mvc.perform(get("/").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void logoutRequiresCsrfAndInvalidatesTheAuthenticatedSession() throws Exception {
        MockHttpSession session = session(submitLogin(username, LoginTestIsolation.PASSWORD)
            .andExpect(redirectedUrl("/")).andReturn());
        mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/").session(session)).andExpect(status().isOk());
        mvc.perform(post("/logout").session(session).with(csrf()))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?logout"));
        assertTrue(session.isInvalid(), "Logout must invalidate the Web session");
        assertTrue(hasAudit("LOGOUT"));
        mvc.perform(get("/")).andExpect(status().is3xxRedirection());
    }

    @Test
    void throttlesAfterTenRealFailedLoginsEvenWhenTheNextPasswordIsCorrect() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            submitLogin(username, "incorrect-test-only-password").andExpect(redirectedUrl("/login?error"));
        }
        submitLogin(username, LoginTestIsolation.PASSWORD).andExpect(redirectedUrl("/login?throttled"));
        assertTrue(hasAudit("LOGIN_THROTTLED"));
        assertFalse(hasAudit("LOGIN_SUCCESS"));
    }

    private MvcResult loginPage() throws Exception {
        return mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
    }

    private org.springframework.test.web.servlet.ResultActions submitLogin(String user, String password) throws Exception {
        MvcResult page = loginPage();
        CsrfToken token = (CsrfToken) page.getRequest().getAttribute("_csrf");
        String hidden = tagWithAttribute(html(page), "input", "name", token.getParameterName());
        return mvc.perform(post("/login").session(session(page))
            .param("username", user).param("password", password)
            .param(token.getParameterName(), attribute(hidden, "value")));
    }

    private void assertMessage(String parameter, String message, String role) throws Exception {
        String html = html(mvc.perform(get("/login").queryParam(parameter, "<script>query-marker</script>"))
            .andExpect(status().isOk()).andReturn());
        assertTrue(html.contains(message), "Expected login status message is missing");
        tagWithAttribute(html, "[a-z][a-z0-9-]*", "role", role);
        assertFalse(html.contains("query-marker"), "Query values must not be echoed");
    }

    private boolean hasAudit(String action) {
        return audit.findAll().stream().anyMatch(event -> action.equals(event.getAction())
            && (username.equals(event.getActorUsername()) || username.equals(event.getTargetReference())));
    }

    private static MockHttpSession session(MvcResult result) {
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session, "Expected a Web session");
        return session;
    }

    private static String html(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static void assertField(String html, String name, String type, String autocomplete, String maxlength) {
        String input = tagWithAttribute(html, "input", "id", name);
        assertEquals(name, attribute(input, "name"));
        assertEquals(type, attribute(input, "type"));
        assertEquals(autocomplete, attribute(input, "autocomplete"));
        assertEquals(maxlength, attribute(input, "maxlength"));
        assertTrue(Pattern.compile("\\srequired(?:\\s|=|/?>)").matcher(input).find(), "Login field is required");
        tagWithAttribute(html, "label", "for", name);
    }

    private static String tagWithAttribute(String html, String tag, String name, String expected) {
        var tags = Pattern.compile("<" + tag + "\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
        while (tags.find()) {
            if (expected.equals(attribute(tags.group(), name))) return tags.group();
        }
        throw new AssertionError("Missing " + tag + " with " + name + "=" + expected);
    }

    private static String attribute(String tag, String name) {
        var attribute = Pattern.compile("\\s" + Pattern.quote(name) + "\\s*=\\s*([\"'])(.*?)\\1",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(tag);
        return attribute.find() ? attribute.group(2) : "";
    }
}
