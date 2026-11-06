package vn.edu.parking.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.*;
import vn.edu.parking.service.PlateNormalizer;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Map;

@Configuration
public class DemoDataConfig {

    @Bean
    CommandLineRunner demoData(VehicleRepository vehicles, ParkingCardRepository cards,
                               PricingRuleRepository prices, HouseholdRepository households,
                               FamilyMemberRepository members, ParkingPolicyRepository policies,
                               ParkingSlotRepository slots, ParkingFloorRepository floors,
                               ParkingZoneRepository zones, VehicleTypeRepository vehicleTypes) {
        return args -> {
            if (!policies.existsById(1L)) {
                ParkingPolicy policy = new ParkingPolicy();
                policy.setId(1L);
                policy.setDefaultMaxTwoWheelers(2);
                policy.setDefaultMaxCars(1);
                policies.save(policy);
            }

            // Seed 6 VehicleTypeEntity
            VehicleTypeEntity vtBicycle = ensureVehicleType(vehicleTypes, "BICYCLE_ELECTRIC_BICYCLE",
                    "Xe đạp / xe đạp điện", VehicleType.BICYCLE_ELECTRIC_BICYCLE, 1);
            VehicleTypeEntity vtMotorbike = ensureVehicleType(vehicleTypes, "MOTORBIKE",
                    "Xe máy", VehicleType.MOTORBIKE, 1);
            VehicleTypeEntity vtLargeMotorbike = ensureVehicleType(vehicleTypes, "LARGE_MOTORBIKE",
                    "Xe máy phân khối lớn", VehicleType.LARGE_MOTORBIKE, 1);
            VehicleTypeEntity vtCar = ensureVehicleType(vehicleTypes, "CAR",
                    "Ô tô", VehicleType.CAR, 5);
            VehicleTypeEntity vtCar67 = ensureVehicleType(vehicleTypes, "CAR_6_7",
                    "Ô tô 6-7 chỗ", VehicleType.CAR_6_7, 5);
            VehicleTypeEntity vtCar89 = ensureVehicleType(vehicleTypes, "CAR_8_9",
                    "Ô tô 8-9 chỗ", VehicleType.CAR_8_9, 5);

            // Seed PricingRules linked to VehicleTypeEntity
            ensurePrice(prices, vtBicycle, VehicleType.BICYCLE_ELECTRIC_BICYCLE,
                2_000, 3_000, 5_000, 100_000, 0, 0, 0);
            ensurePrice(prices, vtMotorbike, VehicleType.MOTORBIKE,
                5_000, 8_000, 10_000, 150_000, 0, 0, 0);
            ensurePrice(prices, vtLargeMotorbike, VehicleType.LARGE_MOTORBIKE,
                5_000, 8_000, 10_000, 300_000, 0, 0, 0);
            ensurePrice(prices, vtCar, VehicleType.CAR,
                40_000, 40_000, 100_000, 1_500_000, 4, 2, 20_000);
            ensurePrice(prices, vtCar67, VehicleType.CAR_6_7,
                40_000, 40_000, 100_000, 1_700_000, 4, 2, 20_000);
            ensurePrice(prices, vtCar89, VehicleType.CAR_8_9,
                40_000, 40_000, 100_000, 1_800_000, 4, 2, 20_000);

            // Seed ParkingFloors
            ParkingFloor floorB1 = floors.findByNameIgnoreCase("Tầng hầm B1").orElseGet(() ->
                floors.save(new ParkingFloor("Tầng hầm B1", true, 1, 500)));
            ParkingFloor floorB2 = floors.findByNameIgnoreCase("Tầng hầm B2").orElseGet(() ->
                floors.save(new ParkingFloor("Tầng hầm B2", true, 2, 500)));

            // Seed ParkingZones
            ParkingZone zoneA = ensureZone(zones, floorB1, "Khu A - Ô tô cư dân", "A", vtCar, 75, "Trái");
            ParkingZone zoneB = ensureZone(zones, floorB1, "Khu B - Ô tô vãng lai", "B", vtCar, 50, "Phải");
            ParkingZone zoneC = ensureZone(zones, floorB1, "Khu C - Xe máy cư dân", "C", vtMotorbike, 15, "Giữa");
            ParkingZone zoneD = ensureZone(zones, floorB2, "Khu D - Ô tô", "D", vtCar, 50, "Trái");
            ParkingZone zoneE = ensureZone(zones, floorB2, "Khu E - Xe máy vãng lai", "E", vtMotorbike, 10, "Phải");

            if (vehicles.count() == 0) {
                Vehicle v1 = new Vehicle();
                v1.setPlateNumber("59A112345");
                v1.setOwnerName("Nguyễn Văn An");
                v1.setOwnerPhone("0901234567");
                v1.setApartmentNumber("A1-1205");
                v1.setVehicleType(VehicleType.MOTORBIKE);
                v1.setVehicleTypeRef(vtMotorbike);
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
                v2.setVehicleTypeRef(vtCar);
                v2.setBrand("VinFast");
                v2.setModelName("VF 8");
                v2.setColor("Trắng");
                v2.setSeatCount(5);
                v2.setFuelType(FuelType.ELECTRIC);
                vehicles.save(v2);
            }

            // Migrate the old demo rows without deleting vehicles or cards.
            for (Vehicle vehicle : vehicles.findAll()) {
                if (vehicle.getVehicleTypeRef() == null) {
                    vehicle.setVehicleTypeRef(resolveVtEntity(vehicleTypes, vehicle.getVehicleType()));
                    vehicles.save(vehicle);
                }
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
                    card.setCardCode(PlateNormalizer.normalize(allVehicles.get(i).getPlateNumber()));
                    card.setVehicle(allVehicles.get(i));
                    card.setStatus(CardStatus.ACTIVE);
                    cards.save(card);
                }
            }

            if (slots.count() == 0) {
                var allVehicles = vehicles.findAll();
                Vehicle carDemo = allVehicles.stream().filter(v -> v.getVehicleType().isCar()).findFirst().orElse(null);

                // Tầng hầm B1 - Khu A (Ô tô cư dân: A01 -> A15)
                for (int i = 1; i <= 15; i++) {
                    ParkingSlot slot = new ParkingSlot();
                    slot.setSlotCode(String.format("A%02d", i));
                    slot.setFloor("Tầng hầm B1");
                    slot.setZoneName("Khu A - Ô tô cư dân");
                    slot.setZone(zoneA);
                    slot.setVehicleTypeRef(vtCar);
                    slot.setSlotType(SlotType.RESIDENT_RESERVED);
                    slot.setAllowedVehicleType(VehicleType.CAR);
                    if (i == 1 && carDemo != null) {
                        slot.setAssignedVehicle(carDemo);
                    } else if (i == 2) {
                        slot.setBorrowedPlate("59X199999");
                        slot.setBorrowedUntil(java.time.LocalDateTime.now().plusHours(3));
                        slot.setBorrowNotes("Hàng xóm để nhờ tạm thời theo lịch hẹn");
                    }
                    slots.save(slot);
                }

                // Tầng hầm B1 - Khu B (Ô tô khách vãng lai: B01 -> B10)
                for (int i = 1; i <= 10; i++) {
                    ParkingSlot slot = new ParkingSlot();
                    slot.setSlotCode(String.format("B%02d", i));
                    slot.setFloor("Tầng hầm B1");
                    slot.setZoneName("Khu B - Ô tô vãng lai");
                    slot.setZone(zoneB);
                    slot.setVehicleTypeRef(vtCar);
                    slot.setSlotType(SlotType.VISITOR_FLEXIBLE);
                    slot.setAllowedVehicleType(VehicleType.CAR);
                    slots.save(slot);
                }

                // Tầng hầm B1 - Khu C (Xe máy cư dân: C01 -> C15)
                for (int i = 1; i <= 15; i++) {
                    ParkingSlot slot = new ParkingSlot();
                    slot.setSlotCode(String.format("C%02d", i));
                    slot.setFloor("Tầng hầm B1");
                    slot.setZoneName("Khu C - Xe máy cư dân");
                    slot.setZone(zoneC);
                    slot.setVehicleTypeRef(vtMotorbike);
                    slot.setSlotType(SlotType.RESIDENT_RESERVED);
                    slot.setAllowedVehicleType(VehicleType.MOTORBIKE);
                    slots.save(slot);
                }

                // Tầng hầm B2 - Khu D (Ô tô mở rộng: D01 -> D10)
                for (int i = 1; i <= 10; i++) {
                    ParkingSlot slot = new ParkingSlot();
                    slot.setSlotCode(String.format("D%02d", i));
                    slot.setFloor("Tầng hầm B2");
                    slot.setZoneName("Khu D - Ô tô");
                    slot.setZone(zoneD);
                    slot.setVehicleTypeRef(vtCar);
                    slot.setSlotType(SlotType.RESIDENT_RESERVED);
                    slot.setAllowedVehicleType(VehicleType.CAR);
                    slots.save(slot);
                }

                // Tầng hầm B2 - Khu E (Xe máy vãng lai: E01 -> E10)
                for (int i = 1; i <= 10; i++) {
                    ParkingSlot slot = new ParkingSlot();
                    slot.setSlotCode(String.format("E%02d", i));
                    slot.setFloor("Tầng hầm B2");
                    slot.setZoneName("Khu E - Xe máy vãng lai");
                    slot.setZone(zoneE);
                    slot.setVehicleTypeRef(vtMotorbike);
                    slot.setSlotType(SlotType.VISITOR_FLEXIBLE);
                    slot.setAllowedVehicleType(VehicleType.MOTORBIKE);
                    slots.save(slot);
                }
            } else {
                // Backfill any unlinked slots
                for (ParkingSlot s : slots.findAll()) {
                    boolean changed = false;
                    if (s.getZone() == null) {
                        String flName = (s.getFloor() != null && !s.getFloor().isBlank()) ? s.getFloor().trim() : "Tầng hầm B1";
                        String znName = (s.getZoneName() != null && !s.getZoneName().isBlank()) ? s.getZoneName().trim() : "Khu A";
                        boolean isBasement = flName.toLowerCase().contains("hầm") || flName.toUpperCase().startsWith("B");
                        ParkingFloor pf = floors.findByNameIgnoreCase(flName)
                                .orElseGet(() -> floors.save(new ParkingFloor(flName, isBasement, 0, 500)));
                        String pfx = (s.getSlotCode() != null && !s.getSlotCode().isEmpty()) ? s.getSlotCode().substring(0, 1).toUpperCase() : "A";
                        VehicleTypeEntity vt = resolveVtEntity(vehicleTypes, s.getAllowedVehicleType());
                        ParkingZone pz = zones.findByFloor_IdAndNameIgnoreCase(pf.getId(), znName)
                                .orElseGet(() -> zones.save(new ParkingZone(pf, znName, pfx, vt, 50, null)));
                        s.setZone(pz);
                        changed = true;
                    }
                    if (s.getVehicleTypeRef() == null) {
                        s.setVehicleTypeRef(resolveVtEntity(vehicleTypes, s.getAllowedVehicleType()));
                        changed = true;
                    }
                    if (changed) {
                        slots.save(s);
                    }
                }
            }
        };
    }

    private static VehicleTypeEntity ensureVehicleType(VehicleTypeRepository repo, String code, String name, VehicleType category, int units) {
        return repo.findByCodeIgnoreCase(code).orElseGet(() ->
            repo.save(new VehicleTypeEntity(code, name, category, units))
        );
    }

    private static ParkingZone ensureZone(ParkingZoneRepository repo, ParkingFloor floor, String name, String prefix, VehicleTypeEntity vt, int capacity, String side) {
        return repo.findByFloor_IdAndNameIgnoreCase(floor.getId(), name).orElseGet(() ->
            repo.save(new ParkingZone(floor, name, prefix, vt, capacity, side))
        );
    }

    private static VehicleTypeEntity resolveVtEntity(VehicleTypeRepository repo, VehicleType category) {
        final VehicleType targetCategory = (category != null) ? category : VehicleType.CAR;
        return repo.findByCodeIgnoreCase(targetCategory.name())
                .or(() -> repo.findFirstByCategoryAndActiveTrueOrderByIdAsc(targetCategory))
                .orElse(null);
    }

    private static void ensurePrice(PricingRuleRepository prices, VehicleTypeEntity vtEntity, VehicleType type,
                                    long dayOrBase, long night, long overnight, long monthly,
                                    int baseHours, int extraHours, long extraPrice) {
        PricingRule rule = null;
        if (vtEntity != null) {
            rule = prices.findByVehicleTypeRef_Id(vtEntity.getId()).orElse(null);
        }
        if (rule == null) {
            rule = prices.findFirstByVehicleTypeOrderByIdAsc(type).orElseGet(PricingRule::new);
        }

        boolean existed = rule.getId() != null;
        rule.setVehicleType(type);
        if (vtEntity != null) {
            rule.setVehicleTypeRef(vtEntity);
        }

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
        if (!existed || rule.getDailyPassPrice().signum() == 0 || legacyMotorbike || legacyCar)
            rule.setDailyPassPrice(BigDecimal.valueOf(overnight));
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
