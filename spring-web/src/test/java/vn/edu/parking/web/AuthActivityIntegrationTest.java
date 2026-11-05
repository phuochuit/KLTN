package vn.edu.parking.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.parking.service.SecurityAuditService;

@SpringBootTest
@AutoConfigureMockMvc
class AuthActivityIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private SecurityAuditService audit;

    @Test
    @WithMockUser(username = "admin", roles = "MANAGEMENT")
    void returnsDesktopLoginEventForWebToast() throws Exception {
        audit.record(null, "LOGIN_SUCCESS", "DESKTOP_SESSION", "dphuoc", "SUCCESS", null, null);

        mvc.perform(get("/auth-activity"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cursor").isNumber())
            .andExpect(jsonPath("$.events").isArray());

        mvc.perform(get("/auth-activity").param("afterId", "0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.events[*].username", hasItem("dphuoc")))
            .andExpect(jsonPath("$.events[*].platform", hasItem("WINFORM")))
            .andExpect(jsonPath("$.events[*].action", hasItem("LOGIN")));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/auth-activity"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "admin", roles = "MANAGEMENT")
    void bootstrapDoesNotReplayOldEvents() throws Exception {
        audit.record(null, "LOGOUT", "WEB_SESSION", "admin", "SUCCESS", null, null);

        mvc.perform(get("/auth-activity"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cursor").isNumber())
            .andExpect(jsonPath("$.events.length()", is(0)));
    }
}
