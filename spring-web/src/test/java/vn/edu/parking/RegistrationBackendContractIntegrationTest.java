package vn.edu.parking;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.edu.parking.domain.Vehicle;
import vn.edu.parking.service.AiProxyService;
import vn.edu.parking.service.ManagementCoreWriteService;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@SpringBootTest
@AutoConfigureMockMvc
class RegistrationBackendContractIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean AiProxyService ai;
    @MockitoBean ManagementCoreWriteService writes;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        LoginTestIsolation.register(registry, "registration-contract");
    }

    @Test
    @WithMockUser(authorities = "VEHICLE_MANAGE")
    void vehicleManagerWithoutCardGrantReturnsToSelectedVehicle() throws Exception {
        stubSavedVehicle();

        mvc.perform(registrationPost())
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/vehicles?vehicleId=42"))
            .andExpect(flash().attribute("message", "Đã lưu hồ sơ hộ, cư dân và xe REG-PLATE."));
    }

    @Test
    @WithMockUser(authorities = {"VEHICLE_MANAGE", "CARD_MANAGE"})
    void cardManagerKeepsSelectedVehicleContextOnCards() throws Exception {
        stubSavedVehicle();

        mvc.perform(registrationPost())
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/cards?vehicleId=42"))
            .andExpect(flash().attribute("message", "Đã lưu hồ sơ hộ, cư dân và xe REG-PLATE."));
    }

    @Test
    @WithMockUser(roles = "MANAGEMENT")
    void managementRoleCanContinueToCards() throws Exception {
        stubSavedVehicle();

        mvc.perform(registrationPost())
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/cards?vehicleId=42"));
    }

    @Test
    @WithMockUser(authorities = "VEHICLE_MANAGE")
    void registrationValidationFailureIsRetrySafeAndReturnsToForm() throws Exception {
        doThrow(new IllegalArgumentException("Dữ liệu đăng ký không hợp lệ"))
            .when(writes).saveRegistration(any(), any(), any());

        mvc.perform(registrationPost())
            .andExpect(status().isBadRequest())
            .andExpect(view().name("management-write-error"))
            .andExpect(model().attribute("returnPath", "/registrations"))
            .andExpect(model().attribute("retrySafe", true))
            .andExpect(content().string(containsString("Thao tác đã bị từ chối")))
            .andExpect(content().string(containsString("Dữ liệu đăng ký không hợp lệ")));
    }

    @Test
    @WithMockUser(authorities = "VEHICLE_MANAGE")
    void registrationConflictIsKnownAndSafeToRetryAfterCorrection() throws Exception {
        doThrow(new DataIntegrityViolationException("synthetic internal detail"))
            .when(writes).saveRegistration(any(), any(), any());

        mvc.perform(registrationPost())
            .andExpect(status().isConflict())
            .andExpect(view().name("management-write-error"))
            .andExpect(model().attribute("returnPath", "/registrations"))
            .andExpect(model().attribute("retrySafe", true))
            .andExpect(content().string(not(containsString("synthetic internal detail"))));
    }

    @Test
    @WithMockUser(authorities = "VEHICLE_MANAGE")
    void registrationUnknownWriteOutcomeMustBeCheckedBeforeRetry() throws Exception {
        doThrow(new DataAccessResourceFailureException("synthetic internal detail"))
            .when(writes).saveRegistration(any(), any(), any());

        mvc.perform(registrationPost())
            .andExpect(status().isServiceUnavailable())
            .andExpect(view().name("management-write-error"))
            .andExpect(model().attribute("returnPath", "/registrations"))
            .andExpect(model().attribute("retrySafe", false))
            .andExpect(content().string(containsString("Không gửi lại ngay")))
            .andExpect(content().string(not(containsString("synthetic internal detail"))));
    }

    private void stubSavedVehicle() {
        Vehicle vehicle = mock(Vehicle.class);
        when(vehicle.getId()).thenReturn(42L);
        when(vehicle.getPlateNumber()).thenReturn("REG-PLATE");
        when(writes.saveRegistration(any(), any(), any())).thenReturn(vehicle);
    }

    private MockHttpServletRequestBuilder registrationPost() {
        return multipart("/registrations").with(csrf())
            .param("householdCode", "REG-HOUSE")
            .param("apartmentNumber", "C1-0101")
            .param("fullName", "Synthetic registrant")
            .param("citizenId", "079205019299")
            .param("plateNumber", "REG-PLATE")
            .param("vehicleType", "MOTORBIKE")
            .param("fuelType", "GASOLINE")
            .param("registrationNumber", "REG-DOC")
            .param("brand", "Synthetic")
            .param("modelName", "Test")
            .param("color", "Blue")
            .param("chassisNumber", "REG-FRAME")
            .param("engineNumber", "REG-ENGINE");
    }
}
