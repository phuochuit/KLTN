package vn.edu.parking.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:csv-test;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@WithMockUser(username = "admin", authorities = {"ROLE_MANAGEMENT", "DASHBOARD_VIEW", "HISTORY_VIEW", "PAYMENT_RECONCILE"})
class CsvExportIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void testExportRevenueCsvWithUtf8Bom() throws Exception {
        MvcResult result = mvc.perform(get("/api/parking/revenue/export-csv")
                .param("type", "month")
                .param("year", "2026")
                .param("month", "10"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("doanh_thu_thang_10_2026.csv")))
                .andReturn();

        byte[] contentBytes = result.getResponse().getContentAsByteArray();
        String content = new String(contentBytes, StandardCharsets.UTF_8);

        assertTrue(content.startsWith(CsvUtils.UTF_8_BOM), "CSV must begin with UTF-8 BOM");
        assertTrue(content.contains("Mã giao dịch / Phiên,Phân loại doanh thu,Biển số xe"));
        assertTrue(content.contains("TỔNG CỘNG,Tất cả nguồn"));
    }

    @Test
    void testExportReconciliationCsvWithUtf8Bom() throws Exception {
        MvcResult result = mvc.perform(get("/payments/reports/reconciliation.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("doi_soat_thanh_toan.csv")))
                .andReturn();

        byte[] contentBytes = result.getResponse().getContentAsByteArray();
        String content = new String(contentBytes, StandardCharsets.UTF_8);

        assertTrue(content.startsWith(CsvUtils.UTF_8_BOM), "CSV must begin with UTF-8 BOM");
        assertTrue(content.contains("Số biên nhận,Loại phí,Phương thức thanh toán,Số tiền (VNĐ)"));
        assertTrue(content.contains("TỔNG CỘNG"));
    }

    @Test
    void testExportExceptionsCsvWithUtf8Bom() throws Exception {
        MvcResult result = mvc.perform(get("/exceptions/export-csv")
                .param("category", "ALL")
                .param("severity", "ALL"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("bao_cao_ngoai_le_")))
                .andReturn();

        byte[] contentBytes = result.getResponse().getContentAsByteArray();
        String content = new String(contentBytes, StandardCharsets.UTF_8);

        assertTrue(content.startsWith(CsvUtils.UTF_8_BOM), "CSV must begin with UTF-8 BOM");
        assertTrue(content.contains("Mã sự kiện,Thời gian,Phân loại,Mức độ,Biển số xe"));
        assertTrue(content.contains("TỔNG CỘNG,,Tổng số sự kiện"));
    }
}
