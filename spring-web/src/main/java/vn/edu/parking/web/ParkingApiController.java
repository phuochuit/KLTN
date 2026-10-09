package vn.edu.parking.web;

import jakarta.validation.Valid;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.parking.domain.SessionStatus;
import vn.edu.parking.domain.PassType;
import vn.edu.parking.domain.SlotStatusOverride;
import vn.edu.parking.domain.ParkingSlot;
import vn.edu.parking.domain.GateOperationType;
import vn.edu.parking.repository.ParkingSessionRepository;
import vn.edu.parking.repository.ParkingCardRepository;
import vn.edu.parking.repository.VehicleRepository;
import vn.edu.parking.repository.ParkingSlotRepository;
import vn.edu.parking.service.PlateNormalizer;
import vn.edu.parking.service.ParkingService;
import vn.edu.parking.service.AnprServiceClient;
import vn.edu.parking.service.AnprServiceUnavailableException;
import vn.edu.parking.service.FaceVerificationPolicy;
import vn.edu.parking.service.GateEvidenceService;
import vn.edu.parking.service.ResidentImageStorage;
import vn.edu.parking.web.dto.*;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/parking")
public class ParkingApiController {
    private final ParkingService parkingService;
    private final ParkingSessionRepository sessions;
    private final VehicleRepository vehicles;
    private final ParkingCardRepository cards;
    private final ParkingSlotRepository slots;
    private final AnprServiceClient anprService;
    private final GateEvidenceService gateEvidence;
    private final ResidentImageStorage residentImageStorage;
    private final FaceVerificationPolicy faceVerificationPolicy;

    public ParkingApiController(ParkingService parkingService, ParkingSessionRepository sessions,
                                VehicleRepository vehicles, ParkingCardRepository cards,
                                ParkingSlotRepository slots, AnprServiceClient anprService,
                                GateEvidenceService gateEvidence, ResidentImageStorage residentImageStorage,
                                FaceVerificationPolicy faceVerificationPolicy) {
        this.parkingService = parkingService;
        this.sessions = sessions;
        this.vehicles = vehicles;
        this.cards = cards;
        this.slots = slots;
        this.anprService = anprService;
        this.gateEvidence = gateEvidence;
        this.residentImageStorage = residentImageStorage;
        this.faceVerificationPolicy = faceVerificationPolicy;
    }

    @PostMapping("/entry")
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGEMENT', 'ROLE_GATE_STAFF')")
    @Transactional
    public ParkingResponse entry(@Valid @RequestBody EntryRequest request, Authentication authentication) {
        rejectLegacyOverrideClaim(request.manualOverride(), request.override());
        if (request.override() != null) requireOverridePermission(authentication);
        GateEvidenceService.Recognition recognition = gateEvidence.requireEntryRecognition(
            request.evidenceId(), request.plateNumber(), authentication, request.override() != null);
        GateEvidenceService.FaceProof faceProof = request.faceEvidenceId() == null ? null
            : gateEvidence.requireEntryFaceEvidence(request.faceEvidenceId(), request.evidenceId(),
                recognition.plateNumber(), request.familyMemberId(), true, authentication);
        if (faceProof != null && "CAPTURED".equals(faceProof.decision())
                && vehicles.findByPlateNumberIgnoreCase(recognition.plateNumber()).isPresent())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Guest face capture cannot be used for a resident vehicle");
        EntryRequest authoritativeRequest = new EntryRequest(recognition.plateNumber(), request.cardCode(),
            recognition.vehicleType(), false, request.familyMemberId(), request.faceVerified(),
            request.faceSimilarity(), null, request.evidenceId(), request.faceEvidenceId(), request.override());
        ParkingResponse response = parkingService.enter(authoritativeRequest, faceProof, recognition, authentication);
        if (faceProof != null) {
            gateEvidence.bindEntryFaceEvidence(faceProof.evidenceId(), request.evidenceId(), recognition.plateNumber(),
                request.familyMemberId(), response.sessionId(), authentication);
            if ("CAPTURED".equals(faceProof.decision())) {
                var session = sessions.findById(response.sessionId()).orElseThrow();
                session.setEntryFaceImagePath("gate-evidence:" + faceProof.evidenceId());
                sessions.save(session);
            }
        }
        gateEvidence.bindEntryToParkingSession(request.evidenceId(), response.sessionId(), authentication);
        return response;
    }

    @PostMapping("/exit-preview")
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGEMENT', 'ROLE_GATE_STAFF')")
    @Transactional
    public ParkingResponse preview(@Valid @RequestBody ExitRequest request, Authentication authentication) {
        rejectLegacyOverrideClaim(request.manualOverride(), request.override());
        if (request.override() != null) requireOverridePermission(authentication);
        Long parkingSessionId = openSessionId(request.plateNumber());
        GateEvidenceService.Recognition recognition = gateEvidence.requireExitRecognition(request.evidenceId(),
            request.plateNumber(), parkingSessionId, authentication, request.override() != null);
        assertUnavailableExitPlateMatchesSession(recognition, request.plateNumber(), parkingSessionId);
        GateEvidenceService.FaceProof faceProof = request.faceEvidenceId() == null ? null
            : gateEvidence.requireExitFaceEvidence(request.faceEvidenceId(), request.evidenceId(),
                request.plateNumber(), parkingSessionId, request.familyMemberId(), true,
                authentication);
        ExitRequest authoritativeRequest = authoritativeExitRequest(request, recognition);
        ParkingResponse response = parkingService.previewExit(authoritativeRequest, faceProof, recognition, authentication);
        gateEvidence.bindExitToParkingSession(request.evidenceId(), recognition.plateNumber(),
            response.sessionId(), authentication);
        if (faceProof != null && !parkingSessionId.equals(response.sessionId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Face evidence belongs to another parking session");
        return response;
    }

    @PostMapping("/exit-confirm")
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGEMENT', 'ROLE_GATE_STAFF')")
    @Transactional
    public ParkingResponse confirm(@Valid @RequestBody ExitRequest request, Authentication authentication) {
        rejectLegacyOverrideClaim(request.manualOverride(), request.override());
        if (request.override() != null) requireOverridePermission(authentication);
        Long parkingSessionId = openSessionId(request.plateNumber());
        GateEvidenceService.Recognition recognition = gateEvidence.requireExitRecognition(request.evidenceId(),
            request.plateNumber(), parkingSessionId, authentication, request.override() != null);
        assertUnavailableExitPlateMatchesSession(recognition, request.plateNumber(), parkingSessionId);
        GateEvidenceService.FaceProof faceProof = request.faceEvidenceId() == null ? null
            : gateEvidence.requireExitFaceEvidence(request.faceEvidenceId(), request.evidenceId(),
                request.plateNumber(), parkingSessionId, request.familyMemberId(), true,
                authentication);
        ExitRequest authoritativeRequest = authoritativeExitRequest(request, recognition);
        ParkingResponse preview = parkingService.previewExit(authoritativeRequest, faceProof, recognition, authentication);
        ParkingResponse response = parkingService.confirmExit(authoritativeRequest, faceProof, recognition, authentication);
        if (faceProof != null)
            gateEvidence.consumeExitFaceEvidence(faceProof.evidenceId(), request.evidenceId(),
                recognition.plateNumber(), preview.sessionId(), request.familyMemberId(), authentication);
        gateEvidence.consumeExitRecognition(request.evidenceId(), recognition.plateNumber(),
            preview.sessionId(), authentication);
        return response;
    }

    @PostMapping("/anpr/face/capture")
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGEMENT', 'ROLE_GATE_STAFF')")
    public FaceEvidenceResponse captureGuestFace(@Valid @RequestBody FaceCaptureRequest request,
            Authentication authentication) {
        String plate = PlateNormalizer.normalize(request.plateNumber());
        if (request.operation() != GateOperationType.ENTRY || plate.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Guest face capture is only valid for vehicle entry");
        JsonNode capture;
        try {
            capture = anprService.captureFaceWithCamera();
        } catch (RuntimeException ex) {
            gateEvidence.recordFaceVerificationFailure(authentication);
            throw ex;
        }
        String imageBase64 = capture.path("realtimeImageBase64").asText("");
        String evidenceId = gateEvidence.recordFaceCapture(decodeAnnotatedImage(imageBase64), plate, authentication);
        return faceResponse(evidenceId, capture);
    }

    @PostMapping("/anpr/face/verify")
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGEMENT', 'ROLE_GATE_STAFF')")
    public FaceEvidenceResponse verifyFace(@Valid @RequestBody FaceVerificationRequest request,
            Authentication authentication) {
        String plate = PlateNormalizer.normalize(request.plateNumber());
        if (plate.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid plate is required");
        if (request.operation() == GateOperationType.ENTRY)
            gateEvidence.requireEntryRecognition(request.evidenceId(), plate, authentication, true);
        byte[] registrationImage;
        Long parkingSessionId = null;
        Long familyMemberId = request.familyMemberId();
        if (request.operation() == GateOperationType.EXIT) {
            var session = sessions.findFirstByEntryPlateIgnoreCaseAndStatusOrderByEntryTimeDesc(plate, SessionStatus.OPEN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Open parking session not found"));
            parkingSessionId = session.getId();
            gateEvidence.requireExitRecognition(request.evidenceId(), plate, parkingSessionId, authentication, true);
            if (session.getVehicle() == null) {
                if (familyMemberId != null)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Guest exit must not select a resident member");
                String entryFaceReference = session.getEntryFaceImagePath();
                if (entryFaceReference == null || entryFaceReference.isBlank())
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Guest entry face image is unavailable");
                if (entryFaceReference.startsWith("gate-evidence:")) {
                    registrationImage = gateEvidence.readGuestEntryFaceImage(entryFaceReference,
                        parkingSessionId, authentication).bytes();
                } else {
                    try {
                        registrationImage = residentImageStorage.readResidentImage(entryFaceReference);
                    } catch (RuntimeException ex) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Guest entry face image is unavailable");
                    }
                }
            } else {
                registrationImage = registeredMemberImage(session.getVehicle(), familyMemberId);
            }
        } else if (request.operation() == GateOperationType.ENTRY) {
            var vehicle = vehicles.findByPlateNumberIgnoreCase(plate)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resident vehicle not found"));
            registrationImage = registeredMemberImage(vehicle, familyMemberId);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported face operation");
        }

        JsonNode verification;
        try {
            verification = anprService.verifyFaceWithCamera(registrationImage);
        } catch (AnprServiceUnavailableException ex) {
            gateEvidence.recordFaceVerificationUnavailable(request.evidenceId(), plate,
                request.operation(), parkingSessionId, authentication);
            return new FaceEvidenceResponse(null, "UNAVAILABLE", 0.0, null,
                "Dịch vụ xác thực khuôn mặt không khả dụng; cần kiểm tra thủ công có lý do.", "");
        } catch (RuntimeException ex) {
            gateEvidence.recordFaceVerificationFailure(request.evidenceId(), authentication);
            throw ex;
        }
        ObjectNode authoritativeVerification = verification.deepCopy();
        String decision = faceVerificationPolicy.decision(
            verification.path("similarity").asDouble(Double.NaN));
        authoritativeVerification.put("decision", decision);
        authoritativeVerification.put("matchThreshold", faceVerificationPolicy.passThreshold());
        authoritativeVerification.put("message", switch (decision) {
            case "PASS" -> "Khuôn mặt đạt ngưỡng do backend quy định";
            case "REVIEW" -> "Kết quả cần nhân viên kiểm tra thủ công";
            default -> "Khuôn mặt không đạt ngưỡng xác minh";
        });
        String imageBase64 = authoritativeVerification.path("realtimeImageBase64").asText("");
        String evidenceId = gateEvidence.recordFaceVerification(request.evidenceId(), decodeAnnotatedImage(imageBase64), plate,
            familyMemberId, request.operation(), parkingSessionId, authentication, authoritativeVerification);
        return faceResponse(evidenceId, authoritativeVerification);
    }

    private byte[] registeredMemberImage(vn.edu.parking.domain.Vehicle vehicle, Long familyMemberId) {
        if (familyMemberId == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an authorized family member");
        var member = vehicle.getAuthorizedMembers().stream()
            .filter(candidate -> candidate.getId().equals(familyMemberId) && candidate.isActive())
            .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Authorized family member not found"));
        if (member.getRegistrationFaceImagePath() == null || member.getRegistrationFaceImagePath().isBlank())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Registered face image is unavailable");
        try {
            return residentImageStorage.readResidentImage(member.getRegistrationFaceImagePath());
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registered face image is unavailable");
        }
    }

    private Long openSessionId(String plate) {
        String normalizedPlate = PlateNormalizer.normalize(plate);
        return sessions.findFirstByEntryPlateIgnoreCaseAndStatusOrderByEntryTimeDesc(normalizedPlate, SessionStatus.OPEN)
            .map(vn.edu.parking.domain.ParkingSession::getId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Open parking session not found"));
    }

    private static FaceEvidenceResponse faceResponse(String evidenceId, JsonNode response) {
        return new FaceEvidenceResponse(evidenceId, response.path("decision").asText(),
            response.path("similarity").asDouble(), response.hasNonNull("matchThreshold")
                ? response.path("matchThreshold").asDouble() : null,
            response.path("message").asText(), response.path("realtimeImageBase64").asText());
    }

    @PostMapping(value = "/anpr/recognize/image", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGEMENT', 'ROLE_GATE_STAFF')")
    public JsonNode recognizeImage(@RequestPart("file") MultipartFile file,
            @RequestParam("operation") GateOperationType operation, Authentication authentication) {
        ValidatedImage image = readAndValidateImage(file);
        String mediaType = switch (image.extension()) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".bmp" -> "image/bmp";
            case ".webp" -> "image/webp";
            default -> throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        };
        String evidenceId = gateEvidence.recordImageCapture(image.bytes(), mediaType, operation, authentication);
        JsonNode recognition;
        try {
            recognition = anprService.recognizeImage(image.bytes(), image.extension());
        } catch (AnprServiceUnavailableException ex) {
            gateEvidence.recordRecognitionUnavailable(evidenceId, authentication);
            return unavailableRecognition(evidenceId);
        } catch (RuntimeException ex) {
            gateEvidence.recordRecognitionFailure(evidenceId, authentication);
            throw ex;
        }
        try {
            gateEvidence.recordRecognition(evidenceId, authentication, recognition);
        } catch (RuntimeException ex) {
            gateEvidence.recordRecognitionFailure(evidenceId, authentication);
            throw ex;
        }
        ObjectNode response = recognition.deepCopy();
        response.put("evidenceId", evidenceId);
        return response;
    }

    @PostMapping(value = "/anpr/recognize/video", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGEMENT', 'ROLE_GATE_STAFF')")
    public JsonNode recognizeVideo(@RequestPart("file") MultipartFile file,
            @RequestParam("operation") GateOperationType operation, Authentication authentication) {
        ValidatedVideo video = readAndValidateVideo(file);
        java.time.Instant capturedAt = java.time.Instant.now();
        JsonNode recognition;
        try {
            recognition = anprService.recognizeVideo(video.bytes(), video.extension());
        } catch (AnprServiceUnavailableException ex) {
            String evidenceId = gateEvidence.recordVideoFailure(video.bytes(), video.extension(), operation,
                authentication, capturedAt);
            return unavailableRecognition(evidenceId);
        } catch (RuntimeException ex) {
            gateEvidence.recordRecognitionFailure(null, authentication);
            throw ex;
        }
        String evidenceId = gateEvidence.recordVideoRecognition(
            decodeAnnotatedImage(recognition.path("annotatedImageBase64").asText()),
            operation, authentication, recognition, capturedAt);
        ObjectNode response = recognition.deepCopy();
        response.put("evidenceId", evidenceId);
        return response;
    }

    private static ValidatedImage readAndValidateImage(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image is required");
        if (file.getSize() > 10L * 1024 * 1024)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Image exceeds the allowed size");
        String filename = file.getOriginalFilename();
        if (filename == null) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        int separator = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String basename = filename.substring(separator + 1);
        int dot = basename.lastIndexOf('.');
        if (dot < 1) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        String extension = basename.substring(dot).toLowerCase(java.util.Locale.ROOT);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (java.io.IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read uploaded image");
        }
        if (!hasImageSignature(extension, bytes))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Image content does not match its format");
        return new ValidatedImage(bytes, extension);
    }

    private static boolean hasImageSignature(String extension, byte[] bytes) {
        return switch (extension) {
            case ".jpg", ".jpeg" -> bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
            case ".png" -> bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P'
                && bytes[2] == 'N' && bytes[3] == 'G' && bytes[4] == 0x0d && bytes[5] == 0x0a
                && bytes[6] == 0x1a && bytes[7] == 0x0a;
            case ".bmp" -> bytes.length >= 2 && bytes[0] == 'B' && bytes[1] == 'M';
            case ".webp" -> bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E'
                && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }

    private static ValidatedVideo readAndValidateVideo(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video is required");
        if (file.getSize() > 10L * 1024 * 1024)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Video exceeds the allowed size");
        String filename = file.getOriginalFilename();
        if (filename == null) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        int separator = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String basename = filename.substring(separator + 1);
        int dot = basename.lastIndexOf('.');
        if (dot < 1) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        String extension = basename.substring(dot).toLowerCase(java.util.Locale.ROOT);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (java.io.IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read uploaded video");
        }
        if (!hasVideoSignature(extension, bytes))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Video content does not match its format");
        return new ValidatedVideo(bytes, extension);
    }

    private static boolean hasVideoSignature(String extension, byte[] bytes) {
        return switch (extension) {
            case ".mp4", ".m4v", ".mov" -> bytes.length >= 12
                && bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p';
            case ".avi" -> bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'A' && bytes[9] == 'V'
                && bytes[10] == 'I' && bytes[11] == ' ';
            case ".mkv", ".webm" -> bytes.length >= 4 && (bytes[0] & 0xff) == 0x1a
                && (bytes[1] & 0xff) == 0x45 && (bytes[2] & 0xff) == 0xdf && (bytes[3] & 0xff) == 0xa3;
            case ".wmv" -> bytes.length >= 16 && (bytes[0] & 0xff) == 0x30
                && (bytes[1] & 0xff) == 0x26 && (bytes[2] & 0xff) == 0xb2 && (bytes[3] & 0xff) == 0x75
                && (bytes[4] & 0xff) == 0x8e && (bytes[5] & 0xff) == 0x66
                && (bytes[6] & 0xff) == 0xcf && (bytes[7] & 0xff) == 0x11
                && (bytes[8] & 0xff) == 0xa6 && (bytes[9] & 0xff) == 0xd9
                && bytes[10] == 0 && (bytes[11] & 0xff) == 0xaa && bytes[12] == 0
                && (bytes[13] & 0xff) == 0x62 && (bytes[14] & 0xff) == 0xce && (bytes[15] & 0xff) == 0x6c;
            default -> false;
        };
    }

    private static byte[] decodeAnnotatedImage(String value) {
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(value);
            if (bytes.length == 0 || bytes.length > 10 * 1024 * 1024)
                throw new IllegalArgumentException("Invalid annotated image");
            return bytes;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ANPR service returned an invalid image");
        }
    }

    private record ValidatedImage(byte[] bytes, String extension) { }
    private record ValidatedVideo(byte[] bytes, String extension) { }

    private static ObjectNode unavailableRecognition(String evidenceId) {
        ObjectNode response = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        response.put("processingStatus", "UNAVAILABLE");
        response.put("evidenceId", evidenceId);
        response.put("plateText", "");
        response.put("vehicleType", "UNKNOWN");
        response.put("detectionConfidence", 0.0);
        response.put("ocrConfidence", 0.0);
        response.put("vehicleConfidence", 0.0);
        response.put("frameIndex", 0);
        response.put("annotatedImageBase64", "");
        response.put("message", "Dịch vụ nhận dạng không khả dụng; cần kiểm tra thủ công có lý do.");
        return response;
    }

    private static void rejectLegacyOverrideClaim(boolean manualOverride, ManualOverrideRequest override) {
        if (manualOverride)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Client manualOverride is not accepted; provide a reasoned override request");
    }

    private static void requireOverridePermission(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(authority -> "OVERRIDE_CREATE".equals(authority.getAuthority())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Override permission is required");
    }

    private void assertUnavailableExitPlateMatchesSession(GateEvidenceService.Recognition recognition,
            String requestedPlate, Long parkingSessionId) {
        if (!recognition.recognitionUnavailable()) return;
        var session = sessions.findById(parkingSessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Open parking session not found"));
        if (!PlateNormalizer.normalize(session.getEntryPlate()).equals(PlateNormalizer.normalize(requestedPlate)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Manual exit plate must match the plate on the open parking session");
    }

    private static ExitRequest authoritativeExitRequest(ExitRequest request,
            GateEvidenceService.Recognition recognition) {
        return new ExitRequest(recognition.plateNumber(), request.cardCode(), recognition.vehicleType(), false,
            request.familyMemberId(), false, null, request.evidenceId(), request.faceEvidenceId(), request.override());
    }

    @GetMapping("/open")
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGEMENT', 'ROLE_GATE_STAFF')")
    public List<OpenParkingSessionResponse> openSessions() {
        return sessions.findByStatusOrderByEntryTimeDesc(SessionStatus.OPEN).stream()
            .map(session -> new OpenParkingSessionResponse(session.getId(), session.getEntryPlate(),
                session.getDetectedVehicleType().name(), session.getEntryTime()))
            .toList();
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGEMENT', 'ROLE_GATE_STAFF')")
    public ResidentLookupResponse lookup(@RequestParam(required = false, defaultValue = "") String plate,
                                         @RequestParam(required = false, defaultValue = "") String cardCode) {
        String normalizedPlate = PlateNormalizer.normalize(plate);
        var vehicle = normalizedPlate.isBlank() ? null
            : vehicles.findByPlateNumberIgnoreCase(normalizedPlate).orElse(null);
        var card = cardCode.isBlank() ? null
            : cards.findByCardCodeIgnoreCase(cardCode.trim()).orElse(null);

        if (vehicle == null && card != null) vehicle = card.getVehicle();
        if (vehicle == null) {
            return new ResidentLookupResponse(false, card == null, normalizedPlate,
                "Khách vãng lai", "", "", "", cardCode.trim().toUpperCase(),
                "", null, false, "Biển số chưa được đăng ký trong danh sách cư dân", java.util.List.of());
        }

        boolean cardMatched = card == null || (card.getVehicle() != null
            && card.getVehicle().getId().equals(vehicle.getId())
            && (normalizedPlate.isBlank() || vehicle.getPlateNumber().equalsIgnoreCase(normalizedPlate)));
        boolean monthlyValid = cardMatched && card != null && card.isMonthlyValid(LocalDate.now());
        String message;
        if (!cardMatched) message = "Thẻ không thuộc biển số đang nhận diện";
        else if (card == null) message = "Xe cư dân đã đăng ký; chưa nhập thẻ nên sẽ tính giá lượt";
        else if (card.getPassType() == PassType.MONTHLY && monthlyValid)
            message = "Gói tháng còn hiệu lực đến " + card.getValidUntil();
        else if (card.getPassType() == PassType.MONTHLY)
            message = "Gói tháng đã hết hạn hoặc chưa đến ngày hiệu lực";
        else message = "Thẻ vé lượt đang hoạt động";
        if (!vehicle.getAuthorizedMemberNames().isBlank()) {
            message += "; người được phép sử dụng: " + vehicle.getAuthorizedMemberNames();
        }

        var authorized = vehicle.getAuthorizedMembers().stream().filter(vn.edu.parking.domain.FamilyMember::isActive)
            .map(m -> new AuthorizedMemberResponse(m.getId(), m.getFullName(), m.getRelationshipToHead(),
                m.getRegistrationFaceImagePath() != null && !m.getRegistrationFaceImagePath().isBlank()))
            .toList();
        return new ResidentLookupResponse(true, cardMatched, vehicle.getPlateNumber(),
            vehicle.getEffectiveOwnerName(), "", vehicle.getEffectiveApartmentNumber(),
            vehicle.getVehicleType().name(), card == null ? "" : card.getCardCode(),
            card == null ? "" : card.getPassType().name(), card == null ? null : card.getValidUntil(),
            monthlyValid, message, authorized);
    }

    @GetMapping("/slots")
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGEMENT', 'ROLE_GATE_STAFF')")
    public List<ParkingSlotResponse> slots() {
        return slots.findAllByOrderBySlotCodeAsc().stream()
            .map(parkingService::toOperationalSlotResponse)
            .toList();
    }

    @PostMapping("/slots/{id}/assign")
    @PreAuthorize("hasAuthority('ROLE_MANAGEMENT')")
    public Map<String, Object> assignVehicle(@PathVariable Long id, @RequestBody SlotAssignRequest req) {
        parkingService.assignVehicleToSlot(id, req.vehicleId());
        return Map.of("success", true, "message", "Đã cập nhật gán xe cho ô đỗ");
    }

    @PostMapping("/slots/{id}/borrow")
    @PreAuthorize("hasAuthority('ROLE_MANAGEMENT')")
    public Map<String, Object> borrowSlot(@PathVariable Long id, @RequestBody SlotBorrowRequest req) {
        parkingService.setupBorrowing(id, req.borrowedPlate(), req.hours() == null ? 2 : req.hours(), req.borrowNotes());
        return Map.of("success", true, "message", "Đã thiết lập xe đỗ nhờ thành công");
    }

    @PostMapping("/slots/{id}/cancel-borrow")
    @PreAuthorize("hasAuthority('ROLE_MANAGEMENT')")
    public Map<String, Object> cancelBorrow(@PathVariable Long id) {
        parkingService.cancelBorrowing(id);
        return Map.of("success", true, "message", "Đã hủy đỗ nhờ");
    }

    @PostMapping("/slots/{id}/status")
    @PreAuthorize("hasAuthority('ROLE_MANAGEMENT')")
    public Map<String, Object> setStatusOverride(@PathVariable Long id, @RequestBody SlotStatusRequest req) {
        SlotStatusOverride override = SlotStatusOverride.valueOf(req.statusOverride().toUpperCase());
        parkingService.setSlotStatusOverride(id, override);
        return Map.of("success", true, "message", "Đã đổi trạng thái ô thành " + override.getDisplayName());
    }

    @PostMapping("/slots/{id}/release")
    @PreAuthorize("hasAuthority('ROLE_MANAGEMENT')")
    public Map<String, Object> releaseSlot(@PathVariable Long id) {
        parkingService.releaseSlot(id);
        return Map.of("success", true, "message", "Đã giải phóng ô đỗ");
    }

    @PostMapping("/slots/{id}/dispatch-session")
    @PreAuthorize("hasAuthority('ROLE_MANAGEMENT')")
    public Map<String, Object> dispatchSession(@PathVariable Long id, @RequestBody DispatchSessionRequest req) {
        parkingService.dispatchSessionToSlot(id, req.sessionId());
        return Map.of("success", true, "message", "Đã điều phối xe vào ô");
    }

    @GetMapping("/recent-unassigned")
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGEMENT', 'ROLE_GATE_STAFF')")
    public List<Map<String, Object>> recentUnassigned() {
        Map<Long, String> sessionSlotMap = slots.findByActiveTrueOrderBySlotCodeAsc().stream()
            .filter(s -> s.getCurrentSession() != null)
            .collect(Collectors.toMap(
                s -> s.getCurrentSession().getId(),
                ParkingSlot::getSlotCode,
                (oldVal, newVal) -> oldVal
            ));

        return sessions.findByStatusOrderByEntryTimeDesc(SessionStatus.OPEN).stream()
            .limit(30)
            .map(s -> {
                String currentSlot = sessionSlotMap.get(s.getId());
                return Map.<String, Object>of(
                    "sessionId", s.getId(),
                    "plateNumber", s.getEntryPlate(),
                    "vehicleType", s.getDetectedVehicleType().name(),
                    "entryTime", s.getEntryTime().toString(),
                    "slotCode", currentSlot == null ? "" : currentSlot
                );
            }).toList();
    }

    @GetMapping("/vehicles-unassigned")
    @PreAuthorize("hasAuthority('ROLE_MANAGEMENT')")
    public List<Map<String, Object>> vehiclesUnassigned() {
        Set<Long> assignedVehicleIds = slots.findByActiveTrueOrderBySlotCodeAsc().stream()
            .filter(s -> s.getAssignedVehicle() != null)
            .map(s -> s.getAssignedVehicle().getId())
            .collect(Collectors.toSet());

        return vehicles.findAll().stream()
            .filter(v -> v.isActive() && !assignedVehicleIds.contains(v.getId()))
            .map(v -> Map.<String, Object>of(
                "id", v.getId(),
                "plateNumber", v.getPlateNumber(),
                "vehicleType", v.getVehicleType().name()
            )).toList();
    }

    @PostMapping("/slots/batch-generate")
    @PreAuthorize("hasAuthority('ROLE_MANAGEMENT')")
    public Map<String, Object> batchGenerate(@RequestBody BatchSlotGenerateRequest req) {
        int count = parkingService.batchGenerateSlots(req);
        return Map.of("success", true, "createdCount", count, "message", "Đã tạo thành công " + count + " ô đỗ");
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "parking-web");
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}
