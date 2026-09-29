package vn.edu.parking.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.*;

import java.math.BigDecimal;
import java.util.LinkedHashSet;

@Configuration
public class DemoDataConfig {
    @Bean
    CommandLineRunner demoData(VehicleRepository vehicles, ParkingCardRepository cards,
                               PricingRuleRepository prices, HouseholdRepository households,
                               FamilyMemberRepository members, ParkingPolicyRepository policies,
                               ParkingSlotRepository slots) {
        return args -> {
            if (!policies.existsById(1L)) {
                ParkingPolicy policy = new ParkingPolicy();
                policy.setId(1L);
                policy.setDefaultMaxTwoWheelers(2);
                policy.setDefaultMaxCars(1);
                policies.save(policy);
            }

            ensurePrice(prices, VehicleType.BICYCLE_ELECTRIC_BICYCLE,
                2_000, 3_000, 5_000, 100_000, 0, 0, 0);
            ensurePrice(prices, VehicleType.MOTORBIKE,
                5_000, 8_000, 10_000, 150_000, 0, 0, 0);
            ensurePrice(prices, VehicleType.LARGE_MOTORBIKE,
                5_000, 8_000, 10_000, 300_000, 0, 0, 0);
            ensurePrice(prices, VehicleType.CAR,
                40_000, 40_000, 100_000, 1_500_000, 4, 2, 20_000);
            ensurePrice(prices, VehicleType.CAR_6_7,
                40_000, 40_000, 100_000, 1_700_000, 4, 2, 20_000);
            ensurePrice(prices, VehicleType.CAR_8_9,
                40_000, 40_000, 100_000, 1_800_000, 4, 2, 20_000);

            if (vehicles.count() == 0) {
                Vehicle v1 = new Vehicle();
                v1.setPlateNumber("59A112345");
                v1.setOwnerName("Nguyễn Văn An");
                v1.setOwnerPhone("0901234567");
                v1.setApartmentNumber("A1-1205");
                v1.setVehicleType(VehicleType.MOTORBIKE);
                v1.setBrand("Honda");
                v1.setModelName("Vision");
                v1.setColor("Đen");
                v1.setFuelType(FuelType.GASOLINE);
                vehicles.save(v1);

                Vehicle v2 = new Vehicle();
                v2.setPlateNumber("51H88888");
                v2.setOwnerName("Trần Minh Châu");
                v2.setOwnerPhone("0912345678");
                v2.setApartmentNumber("B2-0808");
                v2.setVehicleType(VehicleType.CAR);
                v2.setBrand("VinFast");
                v2.setModelName("VF 8");
                v2.setColor("Trắng");
                v2.setSeatCount(5);
                v2.setFuelType(FuelType.ELECTRIC);
                vehicles.save(v2);
            }

            // Migrate the old demo rows without deleting vehicles or cards.
            for (Vehicle vehicle : vehicles.findAll()) {
                if (vehicle.getHousehold() != null) continue;
                String apartment = blankTo(vehicle.getApartmentNumber(), "CHUA-CAP-NHAT");
                String code = "APT-" + apartment.toUpperCase().replaceAll("[^A-Z0-9]", "");
                Household household = households.findByHouseholdCodeIgnoreCase(code).orElseGet(() -> {
                    Household item = new Household();
                    item.setHouseholdCode(code);
                    item.setApartmentNumber(apartment);
                    item.setBuildingName(apartment.contains("-") ? apartment.substring(0, apartment.indexOf('-')) : "");
                    item.setContactPhone(vehicle.getOwnerPhone());
                    return households.save(item);
                });
                FamilyMember owner = new FamilyMember();
                owner.setHousehold(household);
                owner.setFullName(blankTo(vehicle.getOwnerName(), "Cư dân chưa cập nhật"));
                owner.setPhone(vehicle.getOwnerPhone());
                owner.setRelationshipToHead("Chủ phương tiện");
                owner = members.save(owner);
                vehicle.setHousehold(household);
                vehicle.setRegisteredOwner(owner);
                vehicle.setAuthorizedMembers(new LinkedHashSet<>());
                vehicle.getAuthorizedMembers().add(owner);
                vehicle.setApartmentNumber(household.getApartmentNumber());
                vehicle.setOwnerName(owner.getFullName());
                vehicles.save(vehicle);
            }

            if (cards.count() == 0) {
                var allVehicles = vehicles.findAll();
                for (int i = 0; i < Math.min(2, allVehicles.size()); i++) {
                    ParkingCard card = new ParkingCard();
                    card.setCardCode(String.format("CARD%03d", i + 1));
                    card.setVehicle(allVehicles.get(i));
                    card.setStatus(CardStatus.ACTIVE);
                    cards.save(card);
                }
            }
            if (slots.count() == 0) {
                for (int i = 1; i <= 50; i++) {
                    ParkingSlot slot = new ParkingSlot();
                    slot.setSlotCode("A" + i);
                    slot.setZoneName(i <= 25 ? "Khu A - trái" : "Khu A - phải");
                    slots.save(slot);
                }
            }
        };
    }

    private static void ensurePrice(PricingRuleRepository prices, VehicleType type,
                                    long dayOrBase, long night, long overnight, long monthly,
                                    int baseHours, int extraHours, long extraPrice) {
        boolean existed = prices.findByVehicleType(type).isPresent();
        PricingRule rule = prices.findByVehicleType(type).orElseGet(PricingRule::new);
        rule.setVehicleType(type);

        boolean legacyMotorbike = existed && type == VehicleType.MOTORBIKE
            && same(rule.getMonthlyPrice(), 120_000);
        boolean legacyCar = existed && type == VehicleType.CAR
            && same(rule.getBasePrice(), 20_000) && same(rule.getMonthlyPrice(), 1_200_000);
        if (!existed || legacyMotorbike || legacyCar) {
            rule.setBasePrice(BigDecimal.valueOf(dayOrBase));
            rule.setOvernightFee(BigDecimal.valueOf(overnight));
            rule.setMonthlyPrice(BigDecimal.valueOf(monthly));
        }
        if (!existed || rule.getNightPrice() == null || legacyMotorbike || legacyCar)
            rule.setNightPrice(BigDecimal.valueOf(night));
        if (!existed || (type.isCar() && rule.getBaseHours() == 0) || legacyCar) rule.setBaseHours(baseHours);
        if (!existed || (type.isCar() && rule.getExtraBlockHours() == 0) || legacyCar) rule.setExtraBlockHours(extraHours);
        if (!existed || (type.isCar() && rule.getExtraBlockPrice().signum() == 0) || legacyCar)
            rule.setExtraBlockPrice(BigDecimal.valueOf(extraPrice));
        prices.save(rule);
    }

    private static boolean same(BigDecimal value, long expected) {
        return value != null && value.compareTo(BigDecimal.valueOf(expected)) == 0;
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
