package vn.edu.parking;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.parking.domain.ParkingSlot;
import vn.edu.parking.domain.ParkingSession;
import vn.edu.parking.domain.SlotStatusOverride;
import vn.edu.parking.domain.Vehicle;
import vn.edu.parking.domain.VehicleType;
import vn.edu.parking.repository.ParkingSlotRepository;
import vn.edu.parking.repository.ParkingSessionRepository;
import vn.edu.parking.repository.VehicleRepository;
import vn.edu.parking.service.AiProxyService;
import vn.edu.parking.web.dto.ParkingSlotResponse;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@WithMockUser(roles = "MANAGEMENT")
class ParkingSlotsPageIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ParkingSlotRepository slots;
    @Autowired ParkingSessionRepository sessions;
    @Autowired VehicleRepository vehicles;
    @MockitoBean AiProxyService aiProxy;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        LoginTestIsolation.register(registry, "parking-slots-page-test");
    }

    @Test
    void searchAndStatusFiltersApplyTogetherOnTheSlotsPage() throws Exception {
        ParkingSlot available = new ParkingSlot();
        available.setSlotCode("QA-FILTER-01");
        available.setFloor("QA Floor");
        available.setZoneName("QA Zone");
        slots.saveAndFlush(available);

        ParkingSlot blocked = new ParkingSlot();
        blocked.setSlotCode("QA-FILTER-02");
        blocked.setFloor("QA Floor");
        blocked.setZoneName("QA Zone");
        blocked.setStatusOverride(SlotStatusOverride.BLOCKED);
        slots.saveAndFlush(blocked);

        mvc.perform(get("/slots").param("floor", "QA Floor").param("zone", "QA Zone")
                .param("status", "AVAILABLE").param("q", "qa-filter-01"))
            .andExpect(status().isOk())
            .andExpect(view().name("slots"))
            .andExpect(model().attribute("slots", hasSize(1)))
            .andExpect(model().attribute("selectedStatus", "AVAILABLE"))
            .andExpect(content().string(containsString("QA-FILTER-01")))
            .andExpect(content().string(not(containsString("QA-FILTER-02"))))
            .andExpect(content().string(containsString("name=\"_csrf\"")))
            .andExpect(content().string(containsString("Sơ đồ và điều phối bãi đỗ xe")));

        mvc.perform(get("/slots").param("floor", "QA Floor").param("zone", "QA Zone")
                .param("status", "BLOCKED"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("slots", hasSize(1)))
            .andExpect(content().string(containsString("QA-FILTER-02")))
            .andExpect(content().string(not(containsString("QA-FILTER-01"))));
    }

    @Test
    void webSlotGridUsesStableNaturalOrderForMixedCodes() throws Exception {
        String prefix = "N" + UUID.randomUUID().toString().substring(0, 6);
        List<String> insertedCodes = List.of(
            prefix + "-A39", prefix + "-A4", prefix + "-A10", prefix + "-A2",
            prefix + "-B2-X10", prefix + "-B10-X1", prefix + "-B2-X9",
            prefix + "-C04", prefix + "-C4");
        for (String code : insertedCodes) {
            ParkingSlot slot = new ParkingSlot();
            slot.setSlotCode(code);
            slot.setFloor("Natural order QA");
            slot.setZoneName("Natural order QA");
            slots.saveAndFlush(slot);
        }

        var result = mvc.perform(get("/slots").param("q", prefix))
            .andExpect(status().isOk())
            .andReturn();

        @SuppressWarnings("unchecked")
        List<ParkingSlotResponse> responses = (List<ParkingSlotResponse>) result.getModelAndView()
            .getModel().get("slots");
        assertEquals(List.of(
            prefix + "-A2", prefix + "-A4", prefix + "-A10", prefix + "-A39",
            prefix + "-B2-X9", prefix + "-B2-X10", prefix + "-B10-X1",
            prefix + "-C04", prefix + "-C4"),
            responses.stream().map(ParkingSlotResponse::slotCode).toList());
    }

    @Test
    void naturalSortHandlesDigitRunsLongerThanMachineIntegers() throws Exception {
        String smaller = "Z" + "9".repeat(18);
        String larger = "Z1" + "0".repeat(18);
        for (String code : List.of(larger, smaller)) {
            ParkingSlot slot = new ParkingSlot();
            slot.setSlotCode(code);
            slot.setFloor("Long digit QA");
            slot.setZoneName("Long digit QA");
            slots.saveAndFlush(slot);
        }

        var result = mvc.perform(get("/slots"))
            .andExpect(status().isOk())
            .andReturn();
        @SuppressWarnings("unchecked")
        List<ParkingSlotResponse> responses = (List<ParkingSlotResponse>) result.getModelAndView()
            .getModel().get("slots");
        List<String> codes = responses.stream().map(ParkingSlotResponse::slotCode).toList();
        assertTrue(codes.indexOf(smaller) >= 0 && codes.indexOf(smaller) < codes.indexOf(larger));
    }

    @Test
    void assignmentTabExplainsOneVehiclePerSlotRule() throws Exception {
        var result = mvc.perform(get("/slots")).andExpect(status().isOk()).andReturn();
        String html = result.getResponse().getContentAsString();
        assertTrue(html.contains("Chọn xe cư dân chưa có vị trí đỗ"),
            "Assignment tab must explain which residents can be assigned.");
        assertTrue(html.contains("id=\"assignVehicleSelect\""),
            "Assignment tab must expose the resident vehicle selector.");
        assertTrue(html.contains("Mỗi xe chỉ được gán tối đa 1 ô"),
            "Assignment tab must explain the one-vehicle-per-slot rule.");
    }

    @Test
    void attentionCountIncludesEachVisibleMismatchOverdueOrBlockedSlotOnce() throws Exception {
        Vehicle assigned = new Vehicle();
        assigned.setPlateNumber("QA-ASSIGNED-01");
        assigned.setOwnerName("Synthetic owner");
        assigned.setApartmentNumber("QA-01");
        assigned.setVehicleType(VehicleType.CAR);
        assigned = vehicles.saveAndFlush(assigned);

        ParkingSession mismatchedSession = openSession("QA-ATTN-MIS-01");
        ParkingSlot mismatch = attentionSlot("QA-ATTN-MISMATCH");
        mismatch.setAssignedVehicle(assigned);
        mismatch.setCurrentSession(mismatchedSession);
        slots.saveAndFlush(mismatch);

        ParkingSession overdueSession = openSession("QA-ATTN-OVR-01");
        ParkingSlot overdue = attentionSlot("QA-ATTN-OVERDUE");
        overdue.setBorrowedPlate(overdueSession.getEntryPlate());
        overdue.setBorrowedUntil(LocalDateTime.now().minusHours(1));
        overdue.setCurrentSession(overdueSession);
        slots.saveAndFlush(overdue);

        ParkingSlot blocked = attentionSlot("QA-ATTN-BLOCKED");
        blocked.setStatusOverride(SlotStatusOverride.BLOCKED);
        slots.saveAndFlush(blocked);

        mvc.perform(get("/slots").param("q", "QA-ATTN-"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("attentionCount", 3L))
            .andExpect(model().attribute("mismatchCount", 1L))
            .andExpect(model().attribute("overdueCount", 1L))
            .andExpect(model().attribute("blockedCount", 1L));
    }

    private ParkingSlot attentionSlot(String code) {
        ParkingSlot slot = new ParkingSlot();
        slot.setSlotCode(code);
        slot.setFloor("Attention QA Floor");
        slot.setZoneName("Attention QA Zone");
        return slot;
    }

    private ParkingSession openSession(String plate) {
        ParkingSession session = new ParkingSession();
        session.setEntryPlate(plate);
        session.setEntryTime(LocalDateTime.now().minusMinutes(10));
        session.setDetectedVehicleType(VehicleType.CAR);
        return sessions.saveAndFlush(session);
    }
}
