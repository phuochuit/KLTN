package vn.edu.parking.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.parking.domain.FamilyMember;
import vn.edu.parking.domain.FuelType;
import vn.edu.parking.domain.Household;
import vn.edu.parking.domain.ParkingPolicy;
import vn.edu.parking.domain.ParkingSession;
import vn.edu.parking.domain.SessionStatus;
import vn.edu.parking.domain.Vehicle;
import vn.edu.parking.domain.VehicleType;
import vn.edu.parking.repository.FamilyMemberRepository;
import vn.edu.parking.repository.HouseholdRepository;
import vn.edu.parking.repository.ParkingPolicyRepository;
import vn.edu.parking.repository.ParkingSessionRepository;
import vn.edu.parking.repository.VehicleRepository;
import org.springframework.web.multipart.MultipartFile;

/** Transactional write boundary and shared household/member/vehicle invariants. */
@Service
public class ManagementCoreWriteService {
    public record HouseholdInput(String householdCode, String apartmentNumber, String buildingName,
            String contactPhone, Integer maxTwoWheelers, Integer maxCars) { }

    public record MemberInput(Long memberId, Long householdId, String fullName, String citizenId,
            String oldCitizenId, java.time.LocalDate dateOfBirth, String gender, String relationshipToHead,
            String phone, String email, String permanentAddress, java.time.LocalDate cccdIssueDate,
            String cccdIssuePlace, String cccdQrRaw, MultipartFile registrationFaceImage,
            String registrationFaceCapture) { }

    public record VehicleInput(Long vehicleId, Long householdId, Long registeredOwnerId,
            List<Long> authorizedMemberIds, String plateNumber, VehicleType vehicleType,
            FuelType fuelType, String registrationNumber, String brand, String modelName, String color,
            Integer manufactureYear, String chassisNumber, String engineNumber, Integer seatCount,
            Integer cylinderCapacityCc, MultipartFile registrationImage, String registrationImagePath) { }

    public record VehicleQuotaPreview(long activeCount, int limit, boolean householdSpecific) { }

    private final HouseholdRepository households;
    private final FamilyMemberRepository members;
    private final VehicleRepository vehicles;
    private final ParkingPolicyRepository policies;
    private final ParkingSessionRepository sessions;
    private final ResidentImageStorage imageStorage;

    public ManagementCoreWriteService(HouseholdRepository households, FamilyMemberRepository members,
            VehicleRepository vehicles, ParkingPolicyRepository policies, ParkingSessionRepository sessions,
            ResidentImageStorage imageStorage) {
        this.households = households;
        this.members = members;
        this.vehicles = vehicles;
        this.policies = policies;
        this.sessions = sessions;
        this.imageStorage = imageStorage;
    }

    @Transactional
    public Household saveHousehold(HouseholdInput input) {
        HouseholdValues values = validateHousehold(input);
        Household household = households.findByHouseholdCodeIgnoreCaseForUpdate(values.code())
                .orElseGet(Household::new);
        applyHousehold(household, values);
        return households.save(household);
    }

    @Transactional
    public FamilyMember saveMember(MemberInput input) {
        MemberValues values = validateMember(input, false);
        Long existingId = resolveMemberId(input.memberId(), values.citizenId());
        Long oldHouseholdId = existingId == null ? null : members.findHouseholdIdById(existingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thành viên cần cập nhật"));
        Set<Long> lockedHouseholds = lockHouseholds(oldHouseholdId, input.householdId());
        Household household = households.findByIdForUpdate(input.householdId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hộ gia đình"));
        FamilyMember member = saveMemberInHousehold(input, values, household, existingId, oldHouseholdId,
                lockedHouseholds);
        return member;
    }

    @Transactional
    public Vehicle saveVehicle(VehicleInput input) {
        VehicleValues values = validateVehicle(input);
        Set<Long> lockedHouseholds = lockHouseholds(input.householdId());
        Household household = households.findByIdForUpdate(input.householdId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hộ gia đình"));
        FamilyMember owner = members.findByIdForUpdate(input.registeredOwnerId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chủ đăng ký"));
        ensureMemberHousehold(owner, household, lockedHouseholds);
        Vehicle vehicle = resolveVehicleForUpdate(input.vehicleId(), values.plate());
        List<FamilyMember> authorized = resolveAuthorizedMembers(input.authorizedMemberIds(), owner, household);
        ensureVehicleCapacity(vehicle, household, values.type());
        return applyVehicle(input, values, household, owner, authorized, vehicle);
    }

    @Transactional(readOnly = true)
    public VehicleQuotaPreview previewVehicleQuota(Long householdId, VehicleType requestedType, Long vehicleId) {
        Household household = households.findById(householdId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hộ gia đình"));
        boolean car = requestedType.isCar();
        long activeCount = vehicles.findByHouseholdIdAndActiveTrue(householdId).stream()
                .filter(vehicle -> vehicleId == null || !vehicleId.equals(vehicle.getId()))
                .filter(vehicle -> vehicle.getVehicleType().isCar() == car)
                .count();
        Integer householdLimit = car ? household.getMaxCars() : household.getMaxTwoWheelers();
        int limit = householdLimit != null ? householdLimit
                : (car ? policies.findById(1L).orElseGet(ParkingPolicy::new).getDefaultMaxCars()
                        : policies.findById(1L).orElseGet(ParkingPolicy::new).getDefaultMaxTwoWheelers());
        return new VehicleQuotaPreview(activeCount, limit, householdLimit != null);
    }

    @Transactional
    public Vehicle saveRegistration(HouseholdInput householdInput, MemberInput memberInput,
            VehicleInput vehicleInput) {
        HouseholdValues householdValues = validateHousehold(householdInput);
        MemberValues memberValues = validateMember(memberInput, true);
        VehicleValues vehicleValues = validateVehicle(vehicleInput);

        Long targetHouseholdId = households.findIdByHouseholdCodeIgnoreCase(householdValues.code()).orElse(null);
        Long existingMemberId = resolveMemberId(null, memberValues.citizenId());
        Long oldHouseholdId = existingMemberId == null ? null : members.findHouseholdIdById(existingMemberId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thành viên cần cập nhật"));
        Set<Long> lockedHouseholds = lockHouseholds(targetHouseholdId, oldHouseholdId);

        Household household;
        if (targetHouseholdId == null) {
            household = households.findByHouseholdCodeIgnoreCase(householdValues.code()).orElseGet(Household::new);
            if (household.getId() != null && !lockedHouseholds.contains(household.getId())) {
                throw new ManagementWriteConflictException("Hồ sơ hộ vừa thay đổi; vui lòng tải lại và thử lại");
            }
        } else {
            household = households.findByIdForUpdate(targetHouseholdId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hộ gia đình"));
        }
        applyHousehold(household, householdValues);
        household = households.save(household);

        FamilyMember member = saveMemberInHousehold(memberInput, memberValues, household, existingMemberId,
                oldHouseholdId, lockedHouseholds);
        Vehicle vehicle = resolveVehicleForUpdate(null, vehicleValues.plate());
        ensureVehicleCapacity(vehicle, household, vehicleValues.type());
        return applyVehicle(vehicleInput, vehicleValues, household, member, List.of(member), vehicle);
    }

    @Transactional
    public void savePolicy(int defaultMaxTwoWheelers, int defaultMaxCars) {
        if (defaultMaxTwoWheelers < 0 || defaultMaxCars < 0) {
            throw new IllegalArgumentException("Hạn mức xe không được âm");
        }
        ParkingPolicy policy = policies.findByIdForUpdate(1L).orElseGet(ParkingPolicy::new);
        policy.setDefaultMaxTwoWheelers(defaultMaxTwoWheelers);
        policy.setDefaultMaxCars(defaultMaxCars);
        policies.save(policy);
    }

    private FamilyMember saveMemberInHousehold(MemberInput input, MemberValues values, Household household,
            Long existingId, Long oldHouseholdId, Set<Long> lockedHouseholds) {
        FamilyMember member = existingId == null ? new FamilyMember() : members.findByIdForUpdate(existingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thành viên cần cập nhật"));
        if (member.getId() != null && member.getHousehold() != null
                && !lockedHouseholds.contains(member.getHousehold().getId())) {
            throw new ManagementWriteConflictException("Hồ sơ thành viên vừa thay đổi; vui lòng tải lại và thử lại");
        }
        if (member.getId() != null && oldHouseholdId != null
                && !oldHouseholdId.equals(member.getHousehold().getId())) {
            throw new ManagementWriteConflictException("Hồ sơ thành viên vừa thay đổi; vui lòng tải lại và thử lại");
        }
        if (values.citizenId() != null) {
            members.findIdByCitizenId(values.citizenId())
                    .filter(otherId -> member.getId() == null || !otherId.equals(member.getId()))
                    .ifPresent(otherId -> { throw new IllegalArgumentException("Số CCCD đã thuộc một thành viên khác"); });
        }

        List<Vehicle> affected = member.getId() == null ? List.of() : lockAffectedVehicles(member.getId());
        List<Vehicle> owned = affected.stream().filter(vehicle -> vehicle.getRegisteredOwner() != null
                && vehicle.getRegisteredOwner().getId().equals(member.getId())).toList();
        ensureTransferCapacity(owned, household);

        member.setHousehold(household);
        member.setFullName(values.fullName());
        member.setCitizenId(values.citizenId());
        member.setOldCitizenId(values.oldCitizenId());
        member.setDateOfBirth(input.dateOfBirth());
        member.setGender(values.gender());
        member.setRelationshipToHead(values.relationshipToHead());
        member.setPhone(values.phone());
        member.setEmail(values.email());
        member.setPermanentAddress(values.permanentAddress());
        member.setCccdIssueDate(input.cccdIssueDate());
        member.setCccdIssuePlace(values.cccdIssuePlace());
        member.setCccdQrRaw(values.cccdQrRaw());
        String newFaceReference = imageStorage.saveForTransaction(input.registrationFaceImage(),
                input.registrationFaceCapture());
        if (newFaceReference != null) member.setRegistrationFaceImagePath(newFaceReference);
        if (member.getRegistrationFaceImagePath() == null || member.getRegistrationFaceImagePath().isBlank()) {
            throw new IllegalArgumentException("Cần chụp hoặc tải lên ảnh khuôn mặt đăng ký");
        }
        member.setActive(true);
        FamilyMember saved = members.save(member);

        Set<Long> ownedIds = new LinkedHashSet<>();
        for (Vehicle vehicle : owned) {
            ownedIds.add(vehicle.getId());
            vehicle.setHousehold(household);
            vehicle.setOwnerName(saved.getFullName());
            vehicle.setOwnerPhone(saved.getPhone());
            vehicle.setApartmentNumber(household.getApartmentNumber());
            vehicle.getAuthorizedMembers().add(saved);
            removeOutOfHouseholdAuthorizations(vehicle, household);
            vehicles.save(vehicle);
        }
        for (Vehicle vehicle : affected) {
            if (ownedIds.contains(vehicle.getId())) continue;
            if (vehicle.getHousehold() == null || !vehicle.getHousehold().getId().equals(household.getId())) {
                vehicle.getAuthorizedMembers().removeIf(item -> item.getId().equals(saved.getId()));
                vehicles.save(vehicle);
            }
        }
        return saved;
    }

    private Vehicle applyVehicle(VehicleInput input, VehicleValues values, Household household, FamilyMember owner,
            List<FamilyMember> authorizedMembers, Vehicle vehicle) {
        String submittedImagePath = input.registrationImagePath();
        if (submittedImagePath != null && !submittedImagePath.isBlank()
                && (vehicle.getId() == null || !submittedImagePath.equals(vehicle.getRegistrationImagePath()))) {
            throw new IllegalArgumentException("Ảnh giấy đăng ký xe không hợp lệ; vui lòng tải ảnh lên");
        }
        vehicle.setPlateNumber(values.plate());
        vehicle.setHousehold(household);
        vehicle.setRegisteredOwner(owner);
        vehicle.setOwnerName(owner.getFullName());
        vehicle.setOwnerPhone(owner.getPhone());
        vehicle.setApartmentNumber(household.getApartmentNumber());
        vehicle.setVehicleType(values.type());
        vehicle.setFuelType(values.fuelType());
        vehicle.setRegistrationNumber(values.registrationNumber());
        vehicle.setBrand(values.brand());
        vehicle.setModelName(values.modelName());
        vehicle.setColor(values.color());
        vehicle.setManufactureYear(input.manufactureYear());
        vehicle.setChassisNumber(values.chassisNumber());
        vehicle.setEngineNumber(values.engineNumber());
        vehicle.setSeatCount(input.seatCount());
        vehicle.setCylinderCapacityCc(input.cylinderCapacityCc());

        String newImage = imageStorage.saveForTransaction(input.registrationImage(), "");
        if (newImage != null) vehicle.setRegistrationImagePath(newImage);
        if (vehicle.getRegistrationImagePath() == null || vehicle.getRegistrationImagePath().isBlank()) {
            throw new IllegalArgumentException("Ảnh giấy đăng ký xe không được để trống");
        }
        if (vehicle.getRegistrationImagePath().length() > 500) {
            throw new IllegalArgumentException("Ảnh giấy đăng ký xe không hợp lệ");
        }
        vehicle.setAuthorizedMembers(new LinkedHashSet<>(authorizedMembers));
        vehicle.setActive(true);
        Vehicle saved = vehicles.save(vehicle);
        sessions.findLatestByEntryPlateAndStatusForUpdate(values.plate(), SessionStatus.OPEN, PageRequest.of(0, 1))
                .stream().findFirst().ifPresent(openSession -> {
                    openSession.setVehicle(saved);
                    openSession.setDetectedVehicleType(saved.getVehicleType());
                    sessions.save(openSession);
                });
        return saved;
    }

    private Vehicle resolveVehicleForUpdate(Long vehicleId, String plate) {
        Vehicle byPlate = vehicles.findByPlateNumberIgnoreCaseForUpdate(plate).orElse(null);
        if (vehicleId == null) return byPlate == null ? new Vehicle() : byPlate;
        Vehicle byId = vehicles.findByIdForUpdate(vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phương tiện cần cập nhật"));
        if (byPlate != null && !byPlate.getId().equals(byId.getId())) {
            throw new IllegalArgumentException("Biển số đã thuộc một phương tiện khác");
        }
        return byId;
    }

    private List<FamilyMember> resolveAuthorizedMembers(List<Long> ids, FamilyMember owner, Household household) {
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        if (ids != null) uniqueIds.addAll(ids);
        List<FamilyMember> selected = members.findAllById(uniqueIds);
        if (selected.size() != uniqueIds.size()) {
            throw new IllegalArgumentException("Một hoặc nhiều người được chọn không còn tồn tại");
        }
        List<FamilyMember> authorized = new ArrayList<>();
        authorized.add(owner);
        for (FamilyMember member : selected) {
            ensureMemberHousehold(member, household, Set.of(household.getId()));
            if (member.isActive() && !member.getId().equals(owner.getId())) authorized.add(member);
        }
        return authorized;
    }

    private List<Vehicle> lockAffectedVehicles(Long memberId) {
        Map<Long, Vehicle> affected = new LinkedHashMap<>();
        vehicles.findByRegisteredOwnerIdOrderByIdAsc(memberId)
                .forEach(vehicle -> affected.put(vehicle.getId(), vehicle));
        vehicles.findDistinctByAuthorizedMembers_IdOrderByIdAsc(memberId)
                .forEach(vehicle -> affected.put(vehicle.getId(), vehicle));
        return affected.values().stream().sorted(Comparator.comparing(Vehicle::getId)).toList();
    }

    private void removeOutOfHouseholdAuthorizations(Vehicle vehicle, Household household) {
        vehicle.getAuthorizedMembers().removeIf(member -> member.getHousehold() == null
                || !member.getHousehold().getId().equals(household.getId()));
    }

    private void ensureTransferCapacity(List<Vehicle> ownedVehicles, Household destination) {
        long incomingCars = ownedVehicles.stream().filter(Vehicle::isActive)
                .filter(vehicle -> vehicle.getVehicleType().isCar())
                .filter(vehicle -> vehicle.getHousehold() == null
                        || !vehicle.getHousehold().getId().equals(destination.getId())).count();
        long incomingTwoWheelers = ownedVehicles.stream().filter(Vehicle::isActive)
                .filter(vehicle -> vehicle.getVehicleType().isTwoWheeler())
                .filter(vehicle -> vehicle.getHousehold() == null
                        || !vehicle.getHousehold().getId().equals(destination.getId())).count();
        if (incomingCars == 0 && incomingTwoWheelers == 0) return;
        ParkingPolicy policy = policies.findForQuotaCheck(1L).orElseGet(ParkingPolicy::new);
        List<Vehicle> destinationVehicles = vehicles.findByHouseholdIdAndActiveTrue(destination.getId());
        long currentCars = destinationVehicles.stream().filter(vehicle -> vehicle.getVehicleType().isCar()).count();
        long currentTwoWheelers = destinationVehicles.stream()
                .filter(vehicle -> vehicle.getVehicleType().isTwoWheeler()).count();
        int carLimit = destination.getMaxCars() == null ? policy.getDefaultMaxCars() : destination.getMaxCars();
        int twoWheelerLimit = destination.getMaxTwoWheelers() == null
                ? policy.getDefaultMaxTwoWheelers() : destination.getMaxTwoWheelers();
        if (currentCars + incomingCars > carLimit || currentTwoWheelers + incomingTwoWheelers > twoWheelerLimit) {
            throw new IllegalArgumentException("Không thể chuyển thành viên: hộ đích không đủ hạn mức phương tiện");
        }
    }

    private void ensureVehicleCapacity(Vehicle vehicle, Household household, VehicleType requestedType) {
        ParkingPolicy policy = policies.findForQuotaCheck(1L).orElseGet(ParkingPolicy::new);
        int limit = requestedType.isCar()
                ? (household.getMaxCars() == null ? policy.getDefaultMaxCars() : household.getMaxCars())
                : (household.getMaxTwoWheelers() == null
                        ? policy.getDefaultMaxTwoWheelers() : household.getMaxTwoWheelers());
        long count = vehicles.findByHouseholdIdAndActiveTrue(household.getId()).stream()
                .filter(item -> item.getId() == null || vehicle.getId() == null
                        || !item.getId().equals(vehicle.getId()))
                .filter(item -> item.getVehicleType().isCar() == requestedType.isCar())
                .count();
        if (count >= limit) {
            throw new IllegalArgumentException("Hộ " + household.getHouseholdCode()
                    + " đã đạt hạn mức " + limit + (requestedType.isCar() ? " ô tô" : " xe hai bánh"));
        }
    }

    private void ensureMemberHousehold(FamilyMember member, Household household, Set<Long> lockedHouseholds) {
        Long memberHouseholdId = member.getHousehold() == null ? null : member.getHousehold().getId();
        if (memberHouseholdId == null || !memberHouseholdId.equals(household.getId())) {
            throw new IllegalArgumentException("Chủ đăng ký và người được phép sử dụng phải thuộc cùng hộ gia đình");
        }
        if (!lockedHouseholds.contains(memberHouseholdId)) {
            throw new ManagementWriteConflictException("Hồ sơ vừa thay đổi; vui lòng tải lại và thử lại");
        }
    }

    private VehicleValues validateVehicle(VehicleInput input) {
        if (input == null) throw new IllegalArgumentException("Thông tin phương tiện là bắt buộc");
        String plate = PlateNormalizer.normalize(input.plateNumber());
        if (plate.length() < 5 || plate.length() > 20) throw new IllegalArgumentException("Biển số không hợp lệ");
        if (input.vehicleType() == null || input.fuelType() == null) {
            throw new IllegalArgumentException("Loại phương tiện và nhiên liệu là bắt buộc");
        }
        return new VehicleValues(plate, input.vehicleType(), input.fuelType(),
                requiredText(input.registrationNumber(), "Số đăng ký xe", 255),
                requiredText(input.brand(), "Hãng xe", 255), requiredText(input.modelName(), "Dòng xe", 255),
                requiredText(input.color(), "Màu xe", 255),
                requiredText(input.chassisNumber(), "Số khung", 255),
                requiredText(input.engineNumber(), "Số máy", 255));
    }

    private MemberValues validateMember(MemberInput input, boolean registration) {
        if (input == null) throw new IllegalArgumentException("Thông tin thành viên là bắt buộc");
        String citizenId = normalizeDigits(input.citizenId());
        if (registration && citizenId.length() != 12) {
            throw new IllegalArgumentException("Số CCCD phải gồm đúng 12 chữ số");
        }
        return new MemberValues(requiredText(input.fullName(), "Họ tên", 255),
                citizenId.isBlank() ? null : maxLength(citizenId, "Số CCCD", 20),
                maxLength(normalizeDigits(input.oldCitizenId()), "Số CCCD cũ", 20),
                optionalText(input.gender(), "Giới tính", 255),
                optionalText(input.relationshipToHead(), "Quan hệ với chủ hộ", 255),
                optionalText(input.phone(), "Số điện thoại", 255), optionalText(input.email(), "Email", 255),
                optionalText(input.permanentAddress(), "Địa chỉ thường trú", 500),
                optionalText(input.cccdIssuePlace(), "Nơi cấp CCCD", 255),
                optionalText(input.cccdQrRaw(), "Dữ liệu QR CCCD", 1000));
    }

    private HouseholdValues validateHousehold(HouseholdInput input) {
        if (input == null) throw new IllegalArgumentException("Thông tin hộ gia đình là bắt buộc");
        String code = requiredText(input.householdCode(), "Mã hộ", 40).toUpperCase(Locale.ROOT);
        String apartment = requiredText(input.apartmentNumber(), "Căn hộ/phòng", 40).toUpperCase(Locale.ROOT);
        return new HouseholdValues(code, apartment, optionalText(input.buildingName(), "Tòa nhà", 255),
                optionalText(input.contactPhone(), "Số điện thoại liên hệ", 255),
                normalizeOverride(input.maxTwoWheelers()), normalizeOverride(input.maxCars()));
    }

    private Long resolveMemberId(Long requestedId, String citizenId) {
        if (requestedId != null) {
            if (members.findHouseholdIdById(requestedId).isEmpty()) {
                throw new IllegalArgumentException("Không tìm thấy thành viên cần cập nhật");
            }
            return requestedId;
        }
        if (citizenId == null) return null;
        return members.findIdByCitizenId(citizenId).orElse(null);
    }

    private Set<Long> lockHouseholds(Long... ids) {
        TreeSet<Long> orderedIds = new TreeSet<>();
        for (Long id : ids) if (id != null) orderedIds.add(id);
        for (Long id : orderedIds) {
            households.findByIdForUpdate(id)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hộ gia đình"));
        }
        return orderedIds;
    }

    private void applyHousehold(Household household, HouseholdValues values) {
        household.setHouseholdCode(values.code());
        household.setApartmentNumber(values.apartment());
        household.setBuildingName(values.buildingName());
        household.setContactPhone(values.contactPhone());
        household.setMaxTwoWheelers(values.maxTwoWheelers());
        household.setMaxCars(values.maxCars());
        household.setActive(true);
    }

    private Integer normalizeOverride(Integer value) { return value == null || value < 0 ? null : value; }

    private String requiredText(String value, String field, int maximum) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " không được để trống");
        return maxLength(normalized, field, maximum);
    }

    private String optionalText(String value, String field, int maximum) {
        return maxLength(value == null ? "" : value.trim(), field, maximum);
    }

    private String maxLength(String value, String field, int maximum) {
        if (value.length() > maximum) throw new IllegalArgumentException(field + " không được quá " + maximum + " ký tự");
        return value;
    }

    private String normalizeDigits(String value) { return value == null ? "" : value.replaceAll("\\D", ""); }

    private record HouseholdValues(String code, String apartment, String buildingName, String contactPhone,
            Integer maxTwoWheelers, Integer maxCars) { }
    private record MemberValues(String fullName, String citizenId, String oldCitizenId, String gender,
            String relationshipToHead, String phone, String email, String permanentAddress,
            String cccdIssuePlace, String cccdQrRaw) { }
    private record VehicleValues(String plate, VehicleType type, FuelType fuelType, String registrationNumber,
            String brand, String modelName, String color, String chassisNumber, String engineNumber) { }
}
