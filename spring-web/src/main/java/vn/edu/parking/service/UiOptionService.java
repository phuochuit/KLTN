package vn.edu.parking.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.ParkingFloor;
import vn.edu.parking.domain.ParkingZone;
import vn.edu.parking.domain.SlotType;
import vn.edu.parking.domain.UiOption;
import vn.edu.parking.domain.VehicleTypeEntity;
import vn.edu.parking.repository.*;
import vn.edu.parking.web.dto.UiOptionDto;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UiOptionService {

    private final UiOptionRepository uiOptionRepository;
    private final ParkingFloorRepository parkingFloorRepository;
    private final ParkingZoneRepository parkingZoneRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final ParkingSlotRepository parkingSlotRepository;

    public UiOptionService(UiOptionRepository uiOptionRepository,
                           ParkingFloorRepository parkingFloorRepository,
                           ParkingZoneRepository parkingZoneRepository,
                           VehicleTypeRepository vehicleTypeRepository,
                           ParkingSlotRepository parkingSlotRepository) {
        this.uiOptionRepository = uiOptionRepository;
        this.parkingFloorRepository = parkingFloorRepository;
        this.parkingZoneRepository = parkingZoneRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.parkingSlotRepository = parkingSlotRepository;
    }

    @Transactional
    public void recordUse(String category, String rawValue) {
        if (category == null || category.isBlank() || rawValue == null) {
            return;
        }
        String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        Optional<UiOption> opt = uiOptionRepository.findByCategoryIgnoreCaseAndValueIgnoreCase(category.trim(), trimmed);
        if (opt.isPresent()) {
            UiOption existing = opt.get();
            existing.setUseCount(existing.getUseCount() + 1);
            existing.setLastUsedAt(Instant.now());
            uiOptionRepository.save(existing);
        } else {
            UiOption newOption = new UiOption(category.trim(), trimmed);
            uiOptionRepository.save(newOption);
        }
    }

    @Transactional(readOnly = true)
    public List<UiOptionDto> listOptions(String category) {
        if (category == null || category.isBlank()) {
            return List.of();
        }
        String cat = category.trim().toLowerCase();

        Map<String, UiOptionDto> map = new LinkedHashMap<>();

        // 1. First populate from ui_option (sorted by useCount desc)
        List<UiOption> recorded = uiOptionRepository.findByCategoryIgnoreCaseOrderByUseCountDescLastUsedAtDesc(cat);
        for (UiOption uo : recorded) {
            String val = uo.getValue();
            map.putIfAbsent(val.toLowerCase(), new UiOptionDto(val, val, uo.getUseCount()));
        }

        // 2. Add defaults depending on category
        switch (cat) {
            case "floor" -> {
                List<ParkingFloor> floors = parkingFloorRepository.findByActiveTrueOrderByDisplayOrderAsc();
                if (!floors.isEmpty()) {
                    for (ParkingFloor f : floors) {
                        map.putIfAbsent(f.getName().toLowerCase(), new UiOptionDto(f.getName(), f.getName(), 1));
                    }
                } else {
                    List<String> distinctFloors = parkingSlotRepository.findDistinctFloors();
                    for (String df : distinctFloors) {
                        if (df != null && !df.isBlank()) {
                            map.putIfAbsent(df.toLowerCase(), new UiOptionDto(df, df, 1));
                        }
                    }
                }
            }
            case "zone" -> {
                List<ParkingZone> zones = parkingZoneRepository.findByActiveTrueOrderByNameAsc();
                if (!zones.isEmpty()) {
                    for (ParkingZone z : zones) {
                        map.putIfAbsent(z.getName().toLowerCase(), new UiOptionDto(z.getName(), z.getName(), 1));
                    }
                } else {
                    List<String> distinctZones = parkingSlotRepository.findDistinctZones();
                    for (String dz : distinctZones) {
                        if (dz != null && !dz.isBlank()) {
                            map.putIfAbsent(dz.toLowerCase(), new UiOptionDto(dz, dz, 1));
                        }
                    }
                }
            }
            case "prefix" -> {
                for (String pfx : List.of("A", "B", "C", "D", "E", "F", "M")) {
                    map.putIfAbsent(pfx.toLowerCase(), new UiOptionDto(pfx, pfx, 1));
                }
            }
            case "slottype" -> {
                map.putIfAbsent(SlotType.RESIDENT_RESERVED.getDisplayName().toLowerCase(),
                        new UiOptionDto(SlotType.RESIDENT_RESERVED.getDisplayName(), SlotType.RESIDENT_RESERVED.getDisplayName(), 10));
                map.putIfAbsent(SlotType.VISITOR_FLEXIBLE.getDisplayName().toLowerCase(),
                        new UiOptionDto(SlotType.VISITOR_FLEXIBLE.getDisplayName(), SlotType.VISITOR_FLEXIBLE.getDisplayName(), 10));
            }
            case "vehicletype" -> {
                List<VehicleTypeEntity> vts = vehicleTypeRepository.findByActiveTrueOrderByDisplayNameAsc();
                for (VehicleTypeEntity vt : vts) {
                    map.putIfAbsent(vt.getDisplayName().toLowerCase(), new UiOptionDto(vt.getDisplayName(), vt.getDisplayName(), 10));
                }
            }
        }

        return new ArrayList<>(map.values());
    }
}
