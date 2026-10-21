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
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.repository.ParkingCardRepository;
import vn.edu.parking.repository.VehicleRepository;
import vn.edu.parking.repository.HouseholdRepository;
import vn.edu.parking.repository.FamilyMemberRepository;
import vn.edu.parking.repository.PricingRuleRepository;
import vn.edu.parking.repository.SecurityAuditRepository;
import vn.edu.parking.repository.PaymentTransactionRepository;
import vn.edu.parking.repository.ParkingSessionRepository;
import vn.edu.parking.repository.SystemAccountRepository;
import vn.edu.parking.service.ResidentImageStorage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.io.ByteArrayOutputStream;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.anyOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
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
    "parking.upload-dir=target/test-uploads",
    "parking.private-storage-path=target/test-private",
    "parking.evidence.required=false"
})
class ParkingFlowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired VehicleRepository vehicles;
    @Autowired ParkingCardRepository cards;
    @Autowired HouseholdRepository households;
    @Autowired FamilyMemberRepository members;
    @Autowired PricingRuleRepository prices;
    @Autowired SecurityAuditRepository audits;
    @Autowired ResidentImageStorage residentImages;
    @Autowired PaymentTransactionRepository paymentTransactions;
    @Autowired ParkingSessionRepository parkingSessions;
    @Autowired SystemAccountRepository systemAccounts;

    @Test
    @WithMockUser(username = "admin", roles = "MANAGEMENT")
    void completesEntryPreviewAndExitFlow() throws Exception {
        seedCollectorAccount();
        String body = """
            {"plateNumber":"59A1-123.45","cardCode":"59A112345","vehicleType":"MOTORBIKE","manualOverride":false}
            """;

        mvc.perform(post("/api/parking/entry").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plateNumber", is("59A112345")))
            .andExpect(jsonPath("$.status", is("OPEN")))
            .andExpect(jsonPath("$.warning", is(false)));

        mvc.perform(post("/api/parking/exit-preview").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("PREVIEW")))
            .andExpect(jsonPath("$.fee", anyOf(is(5000), is(8000))));

        mvc.perform(post("/api/parking/exit-confirm").contentType("application/json").content(body))
            .andExpect(status().isBadRequest());
        assertThat(parkingSessions.findByStatusOrderByEntryTimeDesc(SessionStatus.OPEN)).hasSize(1);
        assertThat(paymentTransactions.count()).isZero();

        String paidBody = """
            {"plateNumber":"59A1-123.45","cardCode":"59A112345","vehicleType":"MOTORBIKE","manualOverride":false,"paymentMethod":"CASH"}
            """;
        mvc.perform(post("/api/parking/exit-confirm").contentType("application/json").content(paidBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("COMPLETED")))
            .andExpect(jsonPath("$.fee", anyOf(is(5000), is(8000))))
            .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("biên nhận")));
        assertThat(paymentTransactions.count()).isEqualTo(1);
    }

    @Test
    void monthlyPassKeepsOwnerAndMakesVisitFeeZero() throws Exception {
        long paymentCountBefore = paymentTransactions.count();
        int completedCountBefore = parkingSessions
            .findByStatusOrderByEntryTimeDesc(SessionStatus.COMPLETED).size();
        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber("29A006131");
        vehicle.setOwnerName("Cư dân A1-1205");
        vehicle.setOwnerPhone("0901234567");
        vehicle.setApartmentNumber("A1-1205");
        vehicle.setVehicleType(VehicleType.MOTORBIKE);
        vehicle = vehicles.save(vehicle);

        ParkingCard card = new ParkingCard();
        card.setCardCode("29A006131");
        card.setVehicle(vehicle);
        card.setStatus(CardStatus.ACTIVE);
        card.setPassType(PassType.MONTHLY);
        card.setValidFrom(LocalDate.now());
        card.setValidUntil(LocalDate.now().plusDays(29));
        card.setSubscriptionFee(BigDecimal.valueOf(120000));
        cards.save(card);

        String body = """
            {"plateNumber":"29A0-061.31","cardCode":"29A006131","vehicleType":"MOTORBIKE","manualOverride":false}
            """;
        mvc.perform(post("/api/parking/entry").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ownerName", is("Cư dân A1-1205")))
            .andExpect(jsonPath("$.warning", is(false)));
        mvc.perform(post("/api/parking/exit-preview").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fee", is(0)));
        mvc.perform(post("/api/parking/exit-confirm").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("COMPLETED")))
            .andExpect(jsonPath("$.fee", is(0)))
            .andExpect(jsonPath("$.message",
                org.hamcrest.Matchers.containsString("gói tháng còn hiệu lực")))
            .andExpect(jsonPath("$.receiptNumber").doesNotExist())
            .andExpect(jsonPath("$.paymentMethod").doesNotExist());
        assertThat(paymentTransactions.count()).isEqualTo(paymentCountBefore);
        assertThat(parkingSessions.findByStatusOrderByEntryTimeDesc(SessionStatus.COMPLETED))
            .hasSize(completedCountBefore + 1);
    }

    @Test
    void ignoresForeignCardCodeAndUsesRecognizedPlateAsIdentifier() throws Exception {
        Vehicle subscribedVehicle = new Vehicle();
        subscribedVehicle.setPlateNumber("30A999999");
        subscribedVehicle.setOwnerName("Chủ xe có gói");
        subscribedVehicle.setVehicleType(VehicleType.CAR);
        subscribedVehicle = vehicles.save(subscribedVehicle);

        ParkingCard monthly = new ParkingCard();
        monthly.setCardCode("30A999999");
        monthly.setVehicle(subscribedVehicle);
        monthly.setStatus(CardStatus.ACTIVE);
        monthly.setPassType(PassType.MONTHLY);
        monthly.setValidFrom(LocalDate.now());
        monthly.setValidUntil(LocalDate.now().plusDays(30));
        cards.save(monthly);

        String body = """
            {"plateNumber":"77A888888","cardCode":"30A999999","vehicleType":"MOTORBIKE","manualOverride":false}
            """;
        mvc.perform(post("/api/parking/entry").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plateNumber", is("77A888888")))
            .andExpect(jsonPath("$.cardCode", is("77A888888")))
            .andExpect(jsonPath("$.ownerName", is("Khách vãng lai")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "MANAGEMENT")
    void rendersAllAdminPages() throws Exception {
        for (String path : new String[]{"/", "/registrations", "/households", "/vehicles", "/cards", "/pricing", "/settings", "/slots", "/sessions"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "MANAGEMENT")
    void guestFaceIsStoredAtEntryAndRequiredAtExit() throws Exception {
        seedCollectorAccount();
        String onePixelPng = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
        String entry = "{\"plateNumber\":\"77A123456\",\"vehicleType\":\"MOTORBIKE\",\"realtimeFaceImageBase64\":\"" + onePixelPng + "\"}";
        String exitWithoutFace = "{\"plateNumber\":\"77A123456\",\"vehicleType\":\"MOTORBIKE\"}";
        String exitWithFace = "{\"plateNumber\":\"77A123456\",\"vehicleType\":\"MOTORBIKE\",\"faceVerified\":true,\"paymentMethod\":\"CASH\"}";
        mvc.perform(post("/api/parking/entry").contentType("application/json").content(entry))
            .andExpect(status().isOk()).andExpect(jsonPath("$.warning", is(false)));
        mvc.perform(post("/api/parking/exit-preview").contentType("application/json").content(exitWithoutFace))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/parking/exit-confirm").contentType("application/json").content(exitWithFace))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("COMPLETED")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "MANAGEMENT")
    void combinedRegistrationCreatesHouseholdMemberVehicleThenOpensPackageStep() throws Exception {
        byte[] image = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        MockMultipartFile face = new MockMultipartFile("registrationFaceImage", "face.png", "image/png", image);
        MockMultipartFile registration = new MockMultipartFile("registrationImage", "registration.png", "image/png", image);
        mvc.perform(multipart("/registrations").file(face).file(registration).with(csrf())
                .param("householdCode", "HH-COMBINED-01").param("apartmentNumber", "C1-0101")
                .param("buildingName", "C1").param("contactPhone", "0901000001")
                .param("fullName", "Cư dân đăng ký tổng hợp").param("citizenId", "079205019299")
                .param("relationshipToHead", "Chủ hộ").param("phone", "0901000001")
                .param("plateNumber", "61A1-234.56").param("vehicleType", "MOTORBIKE")
                .param("fuelType", "GASOLINE").param("registrationNumber", "DKX-COMBINED")
                .param("brand", "Honda").param("modelName", "Vision").param("color", "Đen")
                .param("chassisNumber", "FRAME-COMBINED").param("engineNumber", "ENGINE-COMBINED"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("/cards?vehicleId=*"));

        Vehicle vehicle = vehicles.findByPlateNumberIgnoreCase("61A123456").orElseThrow();
        assertEquals("HH-COMBINED-01", vehicle.getHousehold().getHouseholdCode());
        assertEquals("Cư dân đăng ký tổng hợp", vehicle.getRegisteredOwner().getFullName());
        assertTrue(vehicle.getRegisteredOwner().getRegistrationFaceImagePath().startsWith("resident:"));
    }

    @Test
    void legacyUploadUrlIsNotPublic() throws Exception {
        mvc.perform(get("/uploads/residents/123e4567-e89b-42d3-a456-426614174000.jpg"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void residentFaceImageRequiresManagementRoleAndCreatesAuditEvent() throws Exception {
        byte[] image = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        Household household = new Household();
        household.setHouseholdCode("HH-FACE-" + suffix);
        household.setApartmentNumber("FACE-" + suffix);
        household = households.save(household);
        FamilyMember member = new FamilyMember();
        member.setHousehold(household);
        member.setFullName("Private Face Test");
        member.setCitizenId("FACE" + suffix);
        member.setRegistrationFaceImagePath(residentImages.save(
            new MockMultipartFile("face", "face.png", "image/png", image), null));
        member = members.save(member);
        Long memberId = member.getId();
        String path = "/admin-data/members/" + memberId + "/face-image";

        mvc.perform(get(path).with(
                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()))
            .andExpect(status().is3xxRedirection());

        long before = audits.findAll().stream()
            .filter(event -> "RESIDENT_FACE_VIEW".equals(event.getAction())).count();
        mvc.perform(get(path).with(
                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                    .user("admin").roles("MANAGEMENT")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/jpeg"))
            .andExpect(header().string("Cache-Control",
                org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(content().bytes(residentImages.readStoredReference(
                member.getRegistrationFaceImagePath())));
        long after = audits.findAll().stream()
            .filter(event -> "RESIDENT_FACE_VIEW".equals(event.getAction())
                && memberId.toString().equals(event.getTargetReference())).count();
        assertEquals(before + 1, after);
    }

    @Test
    @WithMockUser(username = "admin", roles = "MANAGEMENT")
    void scansVietnameseCitizenCardQrOnServerWithoutBrowserBarcodeDetector() throws Exception {
        String raw = "079205019200|123456789|TRUONG TO DINH PHUOC|19032005|Nam|57 Vuon Chuoi, TP HCM|19032030";
        var matrix = new QRCodeWriter().encode(raw, BarcodeFormat.QR_CODE, 500, 500,
            java.util.Map.of(EncodeHintType.CHARACTER_SET, "UTF-8"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", output);
        MockMultipartFile qr = new MockMultipartFile("file", "cccd-qr.png", "image/png", output.toByteArray());
        mvc.perform(multipart("/api/cccd/scan-qr").file(qr).with(csrf()))
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
    @WithMockUser(username = "admin", roles = "MANAGEMENT")
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

    private void seedCollectorAccount() {
        if (systemAccounts.findByUsername("admin").isPresent()) return;
        var account = new SystemAccount("admin", "Test Administrator",
            "test-password-hash", AccountRole.MANAGEMENT, false);
        account.setEnabled(true);
        systemAccounts.save(account);
    }
}
