package vn.edu.parking.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

class WebSessionDevicesTest {

    @Test
    void observeRestoresMissingMetadataFromTheFirstRequestAfterRestart() {
        WebSessionDevices devices = new WebSessionDevices();
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = request(session,
            "Mozilla/5.0 (Linux; Android 14) Chrome/130 Mobile", "100.64.10.7");

        devices.observe(request);

        WebSessionDevices.Device recorded = devices.find(session.getId());
        assertThat(recorded).isNotNull();
        assertThat(recorded.label()).isEqualTo("Chrome · Android");
        assertThat(recorded.kind()).isEqualTo("Điện thoại");
        assertThat(recorded.ip()).isEqualTo("100.64.10.7");
        assertThat(recorded.since()).isNotNull();
    }

    @Test
    void observeDoesNotOverwriteMetadataAlreadyCapturedAtLogin() {
        WebSessionDevices devices = new WebSessionDevices();
        MockHttpSession session = new MockHttpSession();
        devices.register(request(session,
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/130", "100.64.11.1"));
        WebSessionDevices.Device first = devices.find(session.getId());

        devices.observe(request(session,
            "Mozilla/5.0 (Linux; Android 14) Firefox/130 Mobile", "100.64.11.2"));

        assertThat(devices.find(session.getId())).isEqualTo(first);
    }

    private static MockHttpServletRequest request(MockHttpSession session, String userAgent, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        request.addHeader("User-Agent", userAgent);
        request.setRemoteAddr(ip);
        return request;
    }
}
