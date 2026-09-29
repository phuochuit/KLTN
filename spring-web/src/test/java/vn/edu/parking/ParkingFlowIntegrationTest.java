package vn.edu.parking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.ParkingCardRepository;
import vn.edu.parking.repository.VehicleRepository;
import vn.edu.parking.repository.HouseholdRepository;
import vn.edu.parking.repository.FamilyMemberRepository;
import vn.edu.parking.repository.PricingRuleRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.io.ByteArrayOutputStream;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:parking-test;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "parking.upload-dir=target/test-uploads"
})
class ParkingFlowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired VehicleRepository vehicles;
    @Autowired ParkingCardRepository cards;
    @Autowired HouseholdRepository households;
    @Autowired FamilyMemberRepository members;
    @Autowired PricingRuleRepository prices;

    @Test
    void completesEntryPreviewAndExitFlow() throws Exception {
        String body = """
            {"plateNumber":"59A1-123.45","cardCode":"CARD001","vehicleType":"MOTORBIKE","manualOverride":false}
            """;

        mvc.perform(post("/api/parking/entry").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plateNumber", is("59A112345")))
            .andExpect(jsonPath("$.status", is("OPEN")))
            .andExpect(jsonPath("$.warning", is(false)));

        mvc.perform(post("/api/parking/exit-preview").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("PREVIEW")))
            .andExpect(jsonPath("$.fee", is(5000)));

        mvc.perform(post("/api/parking/exit-confirm").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("COMPLETED")))
            .andExpect(jsonPath("$.fee", is(5000)));
    }

    @Test
    void monthlyPassKeepsOwnerAndMakesVisitFeeZero() throws Exception {
        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber("29A006131");
        vehicle.setOwnerName("Cư dân A1-1205");
        vehicle.setOwnerPhone("0901234567");
        vehicle.setApartmentNumber("A1-1205");
        vehicle.setVehicleType(VehicleType.MOTORBIKE);
        vehicle = vehicles.save(vehicle);

        ParkingCard card = new ParkingCard();
        card.setCardCode("CARD030");
        card.setVehicle(vehicle);
        card.setStatus(CardStatus.ACTIVE);
        card.setPassType(PassType.MONTHLY);
        card.setValidFrom(LocalDate.now());
        card.setValidUntil(LocalDate.now().plusDays(29));
        card.setSubscriptionFee(BigDecimal.valueOf(120000));
        cards.save(card);

        String body = """
            {"plateNumber":"29A0-061.31","cardCode":"CARD030","vehicleType":"MOTORBIKE","manualOverride":false}
            """;
        mvc.perform(post("/api/parking/entry").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ownerName", is("Cư dân A1-1205")))
            .andExpect(jsonPath("$.warning", is(false)));
        mvc.perform(post("/api/parking/exit-preview").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fee", is(0)));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void rendersAllAdminPages() throws Exception {
        for (String path : new String[]{"/", "/registrations", "/households", "/vehicles", "/cards", "/pricing", "/settings", "/slots", "/sessions"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
    }

    @Test
    void guestFaceIsStoredAtEntryAndRequiredAtExit() throws Exception {
        String onePixelPng = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
        String entry = "{\"plateNumber\":\"77A123456\",\"vehicleType\":\"MOTORBIKE\",\"realtimeFaceImageBase64\":\"" + onePixelPng + "\"}";
        String exitWithoutFace = "{\"plateNumber\":\"77A123456\",\"vehicleType\":\"MOTORBIKE\"}";
        String exitWithFace = "{\"plateNumber\":\"77A123456\",\"vehicleType\":\"MOTORBIKE\",\"faceVerified\":true}";
        mvc.perform(post("/api/parking/entry").contentType("application/json").content(entry))
            .andExpect(status().isOk()).andExpect(jsonPath("$.warning", is(false)));
        mvc.perform(post("/api/parking/exit-preview").contentType("application/json").content(exitWithoutFace))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/parking/exit-confirm").contentType("application/json").content(exitWithFace))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("COMPLETED")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void combinedRegistrationCreatesHouseholdMemberVehicleThenOpensPackageStep() throws Exception {
        byte[] image = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        MockMultipartFile face = new MockMultipartFile("registrationFaceImage", "face.png", "image/png", image);
        mvc.perform(multipart("/registrations").file(face).with(csrf())
                .param("householdCode", "HH-COMBINED-01").param("apartmentNumber", "C1-0101")
                .param("buildingName", "C1").param("contactPhone", "0901000001")
                .param("fullName", "Cư dân đăng ký tổng hợp").param("citizenId", "079205019299")
                .param("relationshipToHead", "Chủ hộ").param("phone", "0901000001")
                .param("plateNumber", "61A1-234.56").param("vehicleType", "MOTORBIKE")
                .param("fuelType", "GASOLINE").param("registrationNumber", "DKX-COMBINED")
                .param("brand", "Honda").param("modelName", "Vision").param("color", "Đen")
                .param("chassisNumber", "FRAME-COMBINED").param("engineNumber", "ENGINE-COMBINED")
                .param("registrationImagePath", "uploads/vehicle-registration/combined.jpg"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("/cards?vehicleId=*"));

        Vehicle vehicle = vehicles.findByPlateNumberIgnoreCase("61A123456").orElseThrow();
        assertEquals("HH-COMBINED-01", vehicle.getHousehold().getHouseholdCode());
        assertEquals("Cư dân đăng ký tổng hợp", vehicle.getRegisteredOwner().getFullName());
        assertTrue(vehicle.getRegisteredOwner().getRegistrationFaceImagePath().startsWith("/uploads/residents/"));
    }

    @Test
    void scansVietnameseCitizenCardQrOnServerWithoutBrowserBarcodeDetector() throws Exception {
        String raw = "079205019200|123456789|TRUONG TO DINH PHUOC|19032005|Nam|57 Vuon Chuoi, TP HCM|19032030";
        var matrix = new QRCodeWriter().encode(raw, BarcodeFormat.QR_CODE, 500, 500,
            java.util.Map.of(EncodeHintType.CHARACTER_SET, "UTF-8"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", output);
        MockMultipartFile qr = new MockMultipartFile("file", "cccd-qr.png", "image/png", output.toByteArray());
        mvc.perform(multipart("/api/cccd/scan-qr").file(qr))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.citizenId", is("079205019200")))
            .andExpect(jsonPath("$.fullName", is("TRUONG TO DINH PHUOC")))
            .andExpect(jsonPath("$.dateOfBirth", is("2005-03-19")));
    }

    @Test
    void surveyPricesAreTheDefaults() {
        assertEquals(BigDecimal.valueOf(100_000), prices.findByVehicleType(VehicleType.BICYCLE_ELECTRIC_BICYCLE).orElseThrow().getMonthlyPrice());
        assertEquals(BigDecimal.valueOf(150_000), prices.findByVehicleType(VehicleType.MOTORBIKE).orElseThrow().getMonthlyPrice());
        assertEquals(BigDecimal.valueOf(300_000), prices.findByVehicleType(VehicleType.LARGE_MOTORBIKE).orElseThrow().getMonthlyPrice());
        assertEquals(BigDecimal.valueOf(1_500_000), prices.findByVehicleType(VehicleType.CAR).orElseThrow().getMonthlyPrice());
        assertEquals(BigDecimal.valueOf(1_700_000), prices.findByVehicleType(VehicleType.CAR_6_7).orElseThrow().getMonthlyPrice());
        assertEquals(BigDecimal.valueOf(1_800_000), prices.findByVehicleType(VehicleType.CAR_8_9).orElseThrow().getMonthlyPrice());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void familyMembersCanBeAuthorizedForTheSameVehicle() throws Exception {
        Household household = new Household();
        household.setHouseholdCode("HH-TEST-SHARED");
        household.setApartmentNumber("T1-0909");
        household.setMaxTwoWheelers(3);
        household = households.save(household);

        FamilyMember husband = new FamilyMember();
        husband.setHousehold(household);
        husband.setFullName("Người chồng kiểm thử");
        husband = members.save(husband);
        FamilyMember wife = new FamilyMember();
        wife.setHousehold(household);
        wife.setFullName("Người vợ kiểm thử");
        wife = members.save(wife);

        mvc.perform(post("/vehicles").with(csrf())
                .param("householdId", household.getId().toString())
                .param("registeredOwnerId", husband.getId().toString())
                .param("authorizedMemberIds", wife.getId().toString())
                .param("plateNumber", "60A9-999.99")
                .param("vehicleType", "MOTORBIKE")
                .param("fuelType", "GASOLINE")
                .param("registrationNumber", "DKX-TEST-01")
                .param("brand", "Honda")
                .param("modelName", "Vision")
                .param("color", "Đen")
                .param("chassisNumber", "RLH-TEST-CHASSIS")
                .param("engineNumber", "JF66E-TEST-ENGINE")
                .param("registrationImagePath", "uploads/test-registration.jpg"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/vehicles"));

        Vehicle vehicle = vehicles.findByPlateNumberIgnoreCase("60A999999").orElseThrow();
        assertEquals(household.getId(), vehicle.getHousehold().getId());
        assertEquals(2, vehicle.getAuthorizedMembers().size());
        assertTrue(vehicle.getAuthorizedMemberNames().contains("Người chồng kiểm thử"));
        assertTrue(vehicle.getAuthorizedMemberNames().contains("Người vợ kiểm thử"));
    }
}
