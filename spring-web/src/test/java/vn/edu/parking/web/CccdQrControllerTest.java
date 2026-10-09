package vn.edu.parking.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.parking.service.CccdQrService;
import vn.edu.parking.service.SecurityAuditService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.hamcrest.Matchers.is;

class CccdQrControllerTest {
    @Test
    void unexpectedScanFailureReturnsGenericMessageAndIsAudited() throws Exception {
        CccdQrService service = mock(CccdQrService.class);
        SecurityAuditService audit = mock(SecurityAuditService.class);
        when(service.scan(any())).thenThrow(new IllegalStateException("private decoder path"));
        MockMvc mvc = standaloneSetup(new CccdQrController(service, audit)).build();
        MockMultipartFile image = new MockMultipartFile("file", "qr.png", "image/png", new byte[]{1});

        mvc.perform(multipart("/api/cccd/scan-qr").file(image))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", is("Không thể đọc ảnh QR CCCD")));

        verify(audit).recordCurrent("CCCD_QR_SCAN", "CCCD_QR", null, "FAILURE", "SCAN_ERROR", null);
    }
}
