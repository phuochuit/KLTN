package vn.edu.parking.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.*;
import vn.edu.parking.service.OutboxEventService;
import vn.edu.parking.service.UiOptionService;
import vn.edu.parking.service.ZoneCapacityService;
import vn.edu.parking.web.dto.*;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/admin")
public class AdminCatalogApiController {

    private final UiOptionService uiOptionService;
    private final ParkingFloorRepository parkingFloorRepository;
    private final ParkingZoneRepository parkingZoneRepository;
    private final ParkingSlotRepository parkingSlotRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final ZoneCapacityService zoneCapacityService;
    private final OutboxEventService outboxEventService;

    public AdminCatalogApiController(UiOptionService uiOptionService,
                                     ParkingFloorRepository parkingFloorRepository,
                                     ParkingZoneRepository parkingZoneRepository,
                                     ParkingSlotRepository parkingSlotRepository,
                                     VehicleTypeRepository vehicleTypeRepository,
                                     PricingRuleRepository pricingRuleRepository,
                                     ZoneCapacityService zoneCapacityService,
                                     OutboxEventService outboxEventService) {
        this.uiOptionService = uiOptionService;
        this.parkingFloorRepository = parkingFloorRepository;
        this.parkingZoneRepository = parkingZoneRepository;
        this.parkingSlotRepository = parkingSlotRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.pricingRuleRepository = pricingRuleRepository;
        this.zoneCapacityService = zoneCapacityService;
        this.outboxEventService = outboxEventService;
    }

    @GetMapping("/options")
    public List<UiOptionDto> getOptions(@RequestParam String category) {
        return uiOptionService.listOptions(category);
    }

    @GetMapping("/floors/suggest-name")
    public Map<String, String> suggestFloorName(@RequestParam(defaultValue = "false") boolean isBasement) {
        List<ParkingFloor> floors = parkingFloorRepository.findAllByOrderByDisplayOrderAsc();
        String suggested;
        if (isBasement) {
            int maxB = 0;
            Pattern p = Pattern.compile("(?i)^B(\\d+)$");
            for (ParkingFloor f : floors) {
                Matcher m = p.matcher(f.getName().trim());
                if (m.matches()) {
                    try {
                        int num = Integer.parseInt(m.group(1));
                        if (num > maxB) maxB = num;
                    } catch (NumberFormatException ignored) {}
                }
            }
            suggested = "B" + (maxB + 1);
        } else {
            int maxTang = 0;
            Pattern p = Pattern.compile("(?i)^Tầng\\s*(\\d+)$");
            for (ParkingFloor f : floors) {
                Matcher m = p.matcher(f.getName().trim());
                if (m.matches()) {
                    try {
                        int num = Integer.parseInt(m.group(1));
                        if (num > maxTang) maxTang = num;
                    } catch (NumberFormatException ignored) {}
                }
            }
            suggested = "Tầng " + (maxTang + 1);
        }
        return Map.of("suggestedName", suggested);
    }

    @GetMapping("/slots/next-number")
    public NextNumberResponse getNextSlotNumber(@RequestParam(defaultValue = "A") String prefix,
                                                @RequestParam(required = false) String floor) {
        String cleanPrefix = prefix.trim().toUpperCase();
        List<ParkingSlot> slots = parkingSlotRepository.findAll();
        int maxSeq = 0;

        Pattern pattern = Pattern.compile("^" + Pattern.quote(cleanPrefix) + "(\\d+)$", Pattern.CASE_INSENSITIVE);
        for (ParkingSlot s : slots) {
            Matcher m = pattern.matcher(s.getSlotCode().trim());
            if (m.matches()) {
                try {
                    int num = Integer.parseInt(m.group(1));
                    if (num > maxSeq) maxSeq = num;
                } catch (NumberFormatException ignored) {}
            }
        }

        int currentUnits = 0;
        int maxUnits = 500;
        if (floor != null && !floor.isBlank()) {
            Optional<ParkingFloor> pf = parkingFloorRepository.findByNameIgnoreCase(floor.trim());
            if (pf.isPresent()) {
                maxUnits = pf.get().getMaxCapacityUnits();
            }
            List<ParkingSlot> floorSlots = parkingSlotRepository.findByFloorAndActiveTrueOrderBySlotCodeAsc(floor.trim());
            for (ParkingSlot s : floorSlots) {
                if (s.getVehicleTypeRef() != null) {
                    currentUnits += s.getVehicleTypeRef().getCapacityUnits();
                } else if (s.getAllowedVehicleType() != null && s.getAllowedVehicleType().isCar()) {
                    currentUnits += 5;
                } else {
                    currentUnits += 1;
                }
            }
        }

        return new NextNumberResponse(cleanPrefix, floor != null ? floor.trim() : "", maxSeq + 1, currentUnits, maxUnits);
    }

    @GetMapping("/vehicle-types")
    public List<VehicleTypeDto> listVehicleTypes() {
        return vehicleTypeRepository.findByActiveTrueOrderByDisplayNameAsc().stream()
                .map(vt -> new VehicleTypeDto(
                        vt.getId(),
                        vt.getCode(),
                        vt.getDisplayName(),
                        vt.getCategory().name(),
                        vt.getCapacityUnits(),
                        pricingRuleRepository.existsByVehicleTypeRef_Id(vt.getId()),
                        vt.isActive()
                ))
                .toList();
    }

    @PostMapping("/vehicle-types")
    @Transactional
    public VehicleTypeDto createVehicleType(@RequestBody CreateVehicleTypeWithPricingRequest req) {
        if (req.getDisplayName() == null || req.getDisplayName().trim().isEmpty()) {
            throw new IllegalArgumentException("Tên loại phương tiện không được để trống.");
        }
        String displayName = req.getDisplayName().trim();

        if (req.getCategory() == null) {
            throw new IllegalArgumentException("Vui lòng chọn nhóm tính chất phương tiện (Ô tô hoặc Xe hai bánh).");
        }

        if (req.getCapacityUnits() < 1 || req.getCapacityUnits() > 100) {
            throw new IllegalArgumentException("Đơn vị sức chứa quy đổi phải từ 1 đến 100.");
        }

        String code = req.getCode();
        if (code == null || code.trim().isEmpty()) {
            code = toUpperSnakeCase(displayName);
        } else {
            code = code.trim().toUpperCase();
        }

        if (vehicleTypeRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("Mã loại phương tiện '" + code + "' đã tồn tại.");
        }
        if (vehicleTypeRepository.existsByDisplayNameIgnoreCase(displayName)) {
            throw new IllegalArgumentException("Tên loại phương tiện '" + displayName + "' đã tồn tại.");
        }

        // Validate pricing info
        if (req.getBasePrice() == null || req.getBasePrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Giá ngày (cơ sở) không được âm.");
        }
        if (req.getOvernightFee() == null || req.getOvernightFee().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Phí qua đêm không được âm.");
        }
        if (req.getCategory().isCar()) {
            if (req.getBaseHours() == null || req.getBaseHours() < 1) {
                throw new IllegalArgumentException("Số giờ cơ sở cho ô tô phải lớn hơn hoặc bằng 1.");
            }
            if (req.getExtraBlockHours() == null || req.getExtraBlockHours() < 1) {
                throw new IllegalArgumentException("Số giờ mỗi block phụ trội phải lớn hơn hoặc bằng 1.");
            }
            if (req.getExtraBlockPrice() == null || req.getExtraBlockPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Giá mỗi block phụ trội không được âm.");
            }
        }

        VehicleTypeEntity entity = new VehicleTypeEntity(code, displayName, req.getCategory(), req.getCapacityUnits());
        entity = vehicleTypeRepository.save(entity);

        PricingRule rule = new PricingRule();
        rule.setVehicleTypeRef(entity);
        rule.setVehicleType(entity.getCategory());
        rule.setBasePrice(req.getBasePrice());
        rule.setNightPrice(req.getNightPrice() != null ? req.getNightPrice() : req.getBasePrice());
        rule.setOvernightFee(req.getOvernightFee());
        rule.setDailyPassPrice(req.getDailyPassPrice() != null ? req.getDailyPassPrice() : BigDecimal.ZERO);
        rule.setMonthlyPrice(req.getMonthlyPrice() != null ? req.getMonthlyPrice() : BigDecimal.ZERO);
        if (entity.getCategory().isCar()) {
            rule.setBaseHours(req.getBaseHours());
            rule.setExtraBlockHours(req.getExtraBlockHours());
            rule.setExtraBlockPrice(req.getExtraBlockPrice() != null ? req.getExtraBlockPrice() : BigDecimal.ZERO);
        } else {
            rule.setBaseHours(0);
            rule.setExtraBlockHours(0);
            rule.setExtraBlockPrice(BigDecimal.ZERO);
        }
        pricingRuleRepository.save(rule);

        uiOptionService.recordUse("vehicleType", displayName);

        return new VehicleTypeDto(entity.getId(), entity.getCode(), entity.getDisplayName(),
                entity.getCategory().name(), entity.getCapacityUnits(), true, entity.isActive());
    }

    @GetMapping("/zones")
    public List<ParkingZoneDto> listZones() {
        List<ParkingZone> zones = parkingZoneRepository.findByActiveTrueOrderByNameAsc();
        List<ParkingZoneDto> dtoList = new ArrayList<>();
        for (ParkingZone z : zones) {
            ParkingZoneDto dto = new ParkingZoneDto();
            dto.setId(z.getId());
            if (z.getFloor() != null) {
                dto.setFloorId(z.getFloor().getId());
                dto.setFloorName(z.getFloor().getName());
                dto.setBasement(z.getFloor().isBasement());
            }
            dto.setName(z.getName());
            dto.setCodePrefix(z.getCodePrefix());
            if (z.getVehicleType() != null) {
                dto.setVehicleTypeId(z.getVehicleType().getId());
                dto.setVehicleTypeCode(z.getVehicleType().getCode());
                dto.setVehicleTypeName(z.getVehicleType().getDisplayName());
            } else {
                dto.setVehicleTypeName("Chưa xác định");
            }
            dto.setCapacityUnits(z.getCapacityUnits());
            dto.setSide(z.getSide());
            dto.setActive(z.isActive());

            List<ParkingSlot> activeSlots = parkingSlotRepository.findByZone_IdAndActiveTrueOrderBySlotCodeAsc(z.getId());
            dto.setActiveSlotCount(activeSlots.size());
            dto.setOccupiedSlotCodes(zoneCapacityService.findBusySlotCodes(z.getId()));
            dtoList.add(dto);
        }
        return dtoList;
    }

    @PostMapping("/zones/{zoneId}/convert")
    public ZoneConversionPreviewResponse convertZone(@PathVariable Long zoneId,
                                                     @RequestBody ZoneConversionRequest req) {
        String username = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName() : "admin";
        return zoneCapacityService.convert(zoneId, req.getTargetVehicleTypeId(), req.isDryRun(), req.isConfirmLoss(), username);
    }

    @PostMapping("/outbox/{id}/replay")
    public Map<String, Object> replayOutbox(@PathVariable Long id) {
        OutboxEvent event = outboxEventService.replayDeadLetter(id);
        return Map.of("success", true, "eventId", event.getEventId(), "status", event.getStatus().name());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handleBusinessError(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    private static String toUpperSnakeCase(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[đĐ]", "d");
        return normalized.replaceAll("[^a-zA-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .toUpperCase(Locale.ROOT);
    }
}
