package vn.edu.parking.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.PricingRuleRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParkingPricingBoundaryTest {

    private PricingRuleRepository pricingRules;
    private ParkingService parkingService;

    private PricingRule motorbikeRule;
    private PricingRule carRule;

    @BeforeEach
    void setUp() {
        pricingRules = mock(PricingRuleRepository.class);
        parkingService = new ParkingService(
                null, null, null, pricingRules, null, null, null, null, null, null);

        // Bảng giá xe máy: Ngày 5.000 đ (06:00 - 17:59), Đêm 8.000 đ (18:00 - 05:59), Qua đêm 10.000 đ, Tháng 150.000 đ
        motorbikeRule = new PricingRule();
        motorbikeRule.setVehicleType(VehicleType.MOTORBIKE);
        motorbikeRule.setBasePrice(new BigDecimal("5000"));
        motorbikeRule.setNightPrice(new BigDecimal("8000"));
        motorbikeRule.setOvernightFee(new BigDecimal("10000"));
        motorbikeRule.setMonthlyPrice(new BigDecimal("150000"));

        // Bảng giá ô tô: 4h đầu 40.000 đ, mỗi block 2h tiếp theo 20.000 đ, qua đêm tối thiểu 100.000 đ, Tháng 1.500.000 đ
        carRule = new PricingRule();
        carRule.setVehicleType(VehicleType.CAR);
        carRule.setBasePrice(new BigDecimal("40000"));
        carRule.setNightPrice(new BigDecimal("40000"));
        carRule.setBaseHours(4);
        carRule.setExtraBlockHours(2);
        carRule.setExtraBlockPrice(new BigDecimal("20000"));
        carRule.setOvernightFee(new BigDecimal("100000"));
        carRule.setMonthlyPrice(new BigDecimal("1500000"));

        when(pricingRules.findByVehicleType(VehicleType.MOTORBIKE)).thenReturn(Optional.of(motorbikeRule));
        when(pricingRules.findByVehicleType(VehicleType.CAR)).thenReturn(Optional.of(carRule));
    }

    @Test
    @DisplayName("Ca biên ngày bắt đầu (06:00:00): Vào đúng 06:00, ra 10:00 -> Tính giá ngày (5.000 đ)")
    void testMotorbikeDayBoundaryStart_06_00() {
        ParkingSession session = new ParkingSession();
        session.setDetectedVehicleType(VehicleType.MOTORBIKE);
        session.setEntryTime(LocalDateTime.of(2026, 10, 22, 6, 0, 0));

        LocalDateTime exitTime = LocalDateTime.of(2026, 10, 22, 10, 0, 0);
        BigDecimal fee = parkingService.calculateFee(session, exitTime);

        assertEquals(0, new BigDecimal("5000").compareTo(fee), "Vào đúng 06:00 ban ngày phải tính 5.000 đ");
    }

    @Test
    @DisplayName("Ca biên ngày kết thúc (17:59:59): Vào 08:00, ra 17:59:59 -> Trọn vẹn ban ngày -> Tính 5.000 đ")
    void testMotorbikeDayBoundaryEnd_17_59() {
        ParkingSession session = new ParkingSession();
        session.setDetectedVehicleType(VehicleType.MOTORBIKE);
        session.setEntryTime(LocalDateTime.of(2026, 10, 22, 8, 0, 0));

        LocalDateTime exitTime = LocalDateTime.of(2026, 10, 22, 17, 59, 59);
        BigDecimal fee = parkingService.calculateFee(session, exitTime);

        assertEquals(0, new BigDecimal("5000").compareTo(fee), "Ra trước 18:00 (17:59:59) phải tính giá ngày 5.000 đ");
    }

    @Test
    @DisplayName("Ca biên đêm bắt đầu (18:00:00): Vào 08:00, ra đúng 18:00:00 -> Bắt đầu giờ đêm -> Tính giá đêm (8.000 đ)")
    void testMotorbikeNightBoundaryStart_18_00() {
        ParkingSession session = new ParkingSession();
        session.setDetectedVehicleType(VehicleType.MOTORBIKE);
        session.setEntryTime(LocalDateTime.of(2026, 10, 22, 8, 0, 0));

        LocalDateTime exitTime = LocalDateTime.of(2026, 10, 22, 18, 0, 0);
        BigDecimal fee = parkingService.calculateFee(session, exitTime);

        assertEquals(0, new BigDecimal("8000").compareTo(fee), "Ra đúng 18:00:00 đã bước vào khung giờ đêm, phải tính giá đêm 8.000 đ");
    }

    @Test
    @DisplayName("Ca đêm thông thường: Vào 19:00, ra 21:30 cùng ngày -> Tính giá đêm (8.000 đ)")
    void testMotorbikeNightRegular() {
        ParkingSession session = new ParkingSession();
        session.setDetectedVehicleType(VehicleType.MOTORBIKE);
        session.setEntryTime(LocalDateTime.of(2026, 10, 22, 19, 0, 0));

        LocalDateTime exitTime = LocalDateTime.of(2026, 10, 22, 21, 30, 0);
        BigDecimal fee = parkingService.calculateFee(session, exitTime);

        assertEquals(0, new BigDecimal("8000").compareTo(fee), "Gửi trong khung giờ tối phải tính 8.000 đ");
    }

    @Test
    @DisplayName("Ca biên đêm kết thúc (05:59:59): Vào 05:59:59 sáng, ra 08:00 -> Giờ vào thuộc đêm -> Tính giá đêm (8.000 đ)")
    void testMotorbikeNightBoundaryEnd_05_59() {
        ParkingSession session = new ParkingSession();
        session.setDetectedVehicleType(VehicleType.MOTORBIKE);
        session.setEntryTime(LocalDateTime.of(2026, 10, 22, 5, 59, 59));

        LocalDateTime exitTime = LocalDateTime.of(2026, 10, 22, 8, 0, 0);
        BigDecimal fee = parkingService.calculateFee(session, exitTime);

        assertEquals(0, new BigDecimal("8000").compareTo(fee), "Vào trước 06:00 sáng thuộc khung đêm, phải tính 8.000 đ");
    }

    @Test
    @DisplayName("Gửi xe qua mốc nửa đêm (00:00:00): Vào 23:30 hôm nay, ra 00:30 sáng hôm sau -> Phí qua đêm (10.000 đ)")
    void testMotorbikeMidnightCrossing_00_00() {
        ParkingSession session = new ParkingSession();
        session.setDetectedVehicleType(VehicleType.MOTORBIKE);
        session.setEntryTime(LocalDateTime.of(2026, 10, 22, 23, 30, 0));

        LocalDateTime exitTime = LocalDateTime.of(2026, 10, 23, 0, 30, 0);
        BigDecimal fee = parkingService.calculateFee(session, exitTime);

        assertEquals(0, new BigDecimal("10000").compareTo(fee), "Gửi qua mốc 00:00 nửa đêm phải tính phí qua đêm 10.000 đ");
    }

    @Test
    @DisplayName("Xe máy gửi 26 tiếng: Vào ngày 1 08:00, ra ngày 2 10:00 -> 2 chu kỳ ngày đêm -> 2 x 10.000 đ = 20.000 đ")
    void testMotorbikeLongStay_26Hours() {
        ParkingSession session = new ParkingSession();
        session.setDetectedVehicleType(VehicleType.MOTORBIKE);
        session.setEntryTime(LocalDateTime.of(2026, 10, 21, 8, 0, 0));

        LocalDateTime exitTime = LocalDateTime.of(2026, 10, 22, 10, 0, 0);
        BigDecimal fee = parkingService.calculateFee(session, exitTime);

        assertEquals(0, new BigDecimal("20000").compareTo(fee), "Gửi 26 tiếng tính 2 chu kỳ ngày đêm = 20.000 đ");
    }

    @Test
    @DisplayName("Ô tô trong block cơ bản (3.5 giờ <= 4 giờ): Tính giá cơ sở 40.000 đ")
    void testCarWithinBaseHours_4Hours() {
        ParkingSession session = new ParkingSession();
        session.setDetectedVehicleType(VehicleType.CAR);
        session.setEntryTime(LocalDateTime.of(2026, 10, 22, 8, 0, 0));

        LocalDateTime exitTime = LocalDateTime.of(2026, 10, 22, 11, 30, 0);
        BigDecimal fee = parkingService.calculateFee(session, exitTime);

        assertEquals(0, new BigDecimal("40000").compareTo(fee), "Ô tô gửi dưới 4 giờ chỉ tính giá cơ bản 40.000 đ");
    }

    @Test
    @DisplayName("Ô tô gửi vượt block (5 giờ = 4h cơ bản + 1h phụ trội): Tính 40.000 + 20.000 = 60.000 đ")
    void testCarExtraBlocks_5Hours() {
        ParkingSession session = new ParkingSession();
        session.setDetectedVehicleType(VehicleType.CAR);
        session.setEntryTime(LocalDateTime.of(2026, 10, 22, 8, 0, 0));

        LocalDateTime exitTime = LocalDateTime.of(2026, 10, 22, 13, 0, 0);
        BigDecimal fee = parkingService.calculateFee(session, exitTime);

        assertEquals(0, new BigDecimal("60000").compareTo(fee), "Ô tô 5h tính 4h đầu (40k) + 1 block 2h (20k) = 60.000 đ");
    }

    @Test
    @DisplayName("Ô tô gửi 35 tiếng dài ngày: 4h đầu (40k) + 31h (16 block x 20k = 320k) = 360.000 đ (vượt qua đêm tối thiểu 100k)")
    void testCarLongStay_35Hours() {
        ParkingSession session = new ParkingSession();
        session.setDetectedVehicleType(VehicleType.CAR);
        session.setEntryTime(LocalDateTime.of(2026, 10, 21, 8, 0, 0));

        LocalDateTime exitTime = LocalDateTime.of(2026, 10, 22, 19, 0, 0); // 35 hours
        BigDecimal fee = parkingService.calculateFee(session, exitTime);

        assertEquals(0, new BigDecimal("360000").compareTo(fee), "Ô tô 35h tính 40k + 16x20k = 360.000 đ");
    }

    @Test
    @DisplayName("Xe cư dân có vé tháng 30 ngày hợp lệ: Phí gửi xe = 0 đ")
    void testResidentMonthlySubscriptionPass_FeeIsZero() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(101L);
        vehicle.setPlateNumber("59A12345");
        vehicle.setVehicleType(VehicleType.CAR);

        ParkingCard card = new ParkingCard();
        card.setId(201L);
        card.setCardCode("CARD-MONTHLY-01");
        card.setVehicle(vehicle);
        card.setPassType(PassType.MONTHLY);
        card.setStatus(CardStatus.ACTIVE);
        card.setValidFrom(LocalDate.of(2026, 10, 1));
        card.setValidUntil(LocalDate.of(2026, 10, 31));

        ParkingSession session = new ParkingSession();
        session.setVehicle(vehicle);
        session.setParkingCard(card);
        session.setDetectedVehicleType(VehicleType.CAR);
        session.setEntryTime(LocalDateTime.of(2026, 10, 22, 8, 0, 0));

        LocalDateTime exitTime = LocalDateTime.of(2026, 10, 22, 18, 30, 0);
        BigDecimal fee = parkingService.calculateFee(session, exitTime);

        assertEquals(0, BigDecimal.ZERO.compareTo(fee), "Xe cư dân có vé tháng hợp lệ phải có phí gửi bằng 0 đ");
    }
}
