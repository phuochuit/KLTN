package vn.edu.parking.web;

import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.parking.service.AiProxyService;
import vn.edu.parking.service.GateEvidenceService;
import vn.edu.parking.service.RegisteredFaceService;
import vn.edu.parking.service.GuestRegisteredFaceService;
import vn.edu.parking.service.SecurityAuditService;
import vn.edu.parking.domain.EvidenceOperation;
import vn.edu.parking.domain.EvidenceSourceKind;

@RestController
@RequestMapping("/api/ai")
public class AiProxyController {
    private final AiProxyService proxy;
    private final GateEvidenceService evidence;
    private final RegisteredFaceService registeredFaces;
    private final GuestRegisteredFaceService guestFaces;
    private final SecurityAuditService audit;

    public AiProxyController(AiProxyService proxy, GateEvidenceService evidence,
            RegisteredFaceService registeredFaces, GuestRegisteredFaceService guestFaces,
            SecurityAuditService audit) {
        this.proxy = proxy;
        this.evidence = evidence;
        this.registeredFaces = registeredFaces;
        this.guestFaces = guestFaces;
        this.audit = audit;
    }

    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<byte[]> health(@RequestHeader(value = "X-Request-ID", required = false) String supplied) {
        String requestId = requestId(supplied);
        return response(proxy.health(requestId), requestId);
    }

    @PostMapping(value = "/recognize/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<byte[]> recognizeImage(@RequestParam("file") MultipartFile file,
            @RequestParam EvidenceOperation operation, Authentication authentication,
            @RequestHeader(value = "X-Request-ID", required = false) String supplied) {
        String requestId = requestId(supplied);
        byte[] result = proxy.recognizeImage(file, requestId);
        var created = evidence.createVehicle(authentication, operation, EvidenceSourceKind.IMAGE, requestId, result);
        audit.recordCurrent("AI_RECOGNIZE_IMAGE", "GATE_EVIDENCE", created.evidenceId(), "SUCCESS", null, created.evidenceId());
        return response(created.responseBody(), requestId);
    }

    @PostMapping(value = "/recognize/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<byte[]> recognizeVideo(@RequestParam("file") MultipartFile file,
            @RequestParam EvidenceOperation operation, Authentication authentication,
            @RequestHeader(value = "X-Request-ID", required = false) String supplied) {
        String requestId = requestId(supplied);
        byte[] result = proxy.recognizeVideo(file, requestId);
        var created = evidence.createVehicle(authentication, operation, EvidenceSourceKind.VIDEO, requestId, result);
        audit.recordCurrent("AI_RECOGNIZE_VIDEO", "GATE_EVIDENCE", created.evidenceId(), "SUCCESS", null, created.evidenceId());
        return response(created.responseBody(), requestId);
    }

    @PostMapping(value = "/recognize/camera", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<byte[]> recognizeCamera(@RequestBody(required = false) RecognitionCameraRequest request,
            Authentication authentication,
            @RequestHeader(value = "X-Request-ID", required = false) String supplied) {
        String requestId = requestId(supplied);
        int cameraIndex = request == null ? 0 : request.cameraIndex();
        int frameCount = request == null ? 8 : request.frameCount();
        EvidenceOperation operation = request == null ? null : request.operation();
        if (operation == null) throw new IllegalArgumentException("Thiếu chiều nghiệp vụ cho evidence");
        byte[] result = proxy.recognizeCamera(cameraIndex, request.cameraSource(), frameCount, requestId);
        var created = evidence.createVehicle(authentication, operation, EvidenceSourceKind.CAMERA, requestId, result);
        audit.recordCurrent("AI_RECOGNIZE_CAMERA", "GATE_EVIDENCE", created.evidenceId(), "SUCCESS", null, created.evidenceId());
        return response(created.responseBody(), requestId);
    }

    @PostMapping(value = "/face/verify-camera", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<byte[]> verifyCamera(@RequestBody FaceCameraRequest request, Authentication authentication,
            @RequestHeader(value = "X-Request-ID", required = false) String supplied) {
        String requestId = requestId(supplied);
        var registered = registeredFaces.load(request.familyMemberId());
        byte[] result = proxy.verifyCamera(registered.bytes(), request.cameraIndex(), request.cameraSource(), requestId);
        var created = evidence.createFace(authentication, request.operation(), EvidenceSourceKind.CAMERA,
            registered.familyMemberId(), requestId, result);
        audit.recordCurrent("AI_FACE_VERIFY", "GATE_EVIDENCE", created.evidenceId(), "SUCCESS", null, created.evidenceId());
        return response(created.responseBody(), requestId);
    }

    @PostMapping(value = "/face/verify-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<byte[]> verifyFaceImage(@RequestParam Long familyMemberId,
            @RequestParam EvidenceOperation operation,
            @RequestParam("realtime") MultipartFile file,
            Authentication authentication,
            @RequestHeader(value = "X-Request-ID", required = false) String supplied) {
        String requestId = requestId(supplied);
        var registered = registeredFaces.load(familyMemberId);
        byte[] result = proxy.verifyFaceImage(registered.bytes(), file, requestId);
        var created = evidence.createFace(authentication, operation, EvidenceSourceKind.IMAGE,
            registered.familyMemberId(), requestId, result);
        audit.recordCurrent("AI_FACE_VERIFY_IMAGE", "GATE_EVIDENCE", created.evidenceId(), "SUCCESS", null, created.evidenceId());
        return response(created.responseBody(), requestId);
    }

    @PostMapping(value = "/face/verify-video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<byte[]> verifyFaceVideo(@RequestParam Long familyMemberId,
            @RequestParam EvidenceOperation operation,
            @RequestParam("realtime") MultipartFile file,
            Authentication authentication,
            @RequestHeader(value = "X-Request-ID", required = false) String supplied) {
        String requestId = requestId(supplied);
        var registered = registeredFaces.load(familyMemberId);
        byte[] result = proxy.verifyFaceVideo(registered.bytes(), file, requestId);
        var created = evidence.createFace(authentication, operation, EvidenceSourceKind.VIDEO,
            registered.familyMemberId(), requestId, result);
        audit.recordCurrent("AI_FACE_VERIFY_VIDEO", "GATE_EVIDENCE", created.evidenceId(), "SUCCESS", null, created.evidenceId());
        return response(created.responseBody(), requestId);
    }

    @PostMapping(value = "/face/guest/verify-camera", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<byte[]> verifyGuestCamera(@RequestBody GuestFaceCameraRequest request,
            Authentication authentication,
            @RequestHeader(value = "X-Request-ID", required = false) String supplied) {
        String requestId = requestId(supplied);
        var registered = guestFaces.loadForExit(request.vehicleEvidenceId());
        byte[] result = proxy.verifyCamera(registered.bytes(), request.cameraIndex(), request.cameraSource(), requestId);
        var created = evidence.createFace(authentication, EvidenceOperation.EXIT,
            EvidenceSourceKind.CAMERA, null, requestId, result);
        audit.recordCurrent("AI_GUEST_FACE_VERIFY", "GATE_EVIDENCE", created.evidenceId(),
            "SUCCESS", null, created.evidenceId());
        return response(created.responseBody(), requestId);
    }

    @PostMapping(value = "/face/guest/verify-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<byte[]> verifyGuestImage(@RequestParam String vehicleEvidenceId,
            @RequestParam("realtime") MultipartFile file, Authentication authentication,
            @RequestHeader(value = "X-Request-ID", required = false) String supplied) {
        String requestId = requestId(supplied);
        var registered = guestFaces.loadForExit(vehicleEvidenceId);
        byte[] result = proxy.verifyFaceImage(registered.bytes(), file, requestId);
        var created = evidence.createFace(authentication, EvidenceOperation.EXIT,
            EvidenceSourceKind.IMAGE, null, requestId, result);
        audit.recordCurrent("AI_GUEST_FACE_VERIFY_IMAGE", "GATE_EVIDENCE", created.evidenceId(),
            "SUCCESS", null, created.evidenceId());
        return response(created.responseBody(), requestId);
    }

    @PostMapping(value = "/face/guest/verify-video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<byte[]> verifyGuestVideo(@RequestParam String vehicleEvidenceId,
            @RequestParam("realtime") MultipartFile file, Authentication authentication,
            @RequestHeader(value = "X-Request-ID", required = false) String supplied) {
        String requestId = requestId(supplied);
        var registered = guestFaces.loadForExit(vehicleEvidenceId);
        byte[] result = proxy.verifyFaceVideo(registered.bytes(), file, requestId);
        var created = evidence.createFace(authentication, EvidenceOperation.EXIT,
            EvidenceSourceKind.VIDEO, null, requestId, result);
        audit.recordCurrent("AI_GUEST_FACE_VERIFY_VIDEO", "GATE_EVIDENCE", created.evidenceId(),
            "SUCCESS", null, created.evidenceId());
        return response(created.responseBody(), requestId);
    }

    @PostMapping(value = "/face/capture-camera", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<byte[]> captureCamera(@RequestBody CameraCaptureRequest request,
            Authentication authentication,
            @RequestHeader(value = "X-Request-ID", required = false) String supplied) {
        String requestId = requestId(supplied);
        byte[] result = proxy.captureCamera(request.cameraIndex(), request.cameraSource(), requestId);
        var created = evidence.createFace(authentication, request.operation(), EvidenceSourceKind.CAMERA,
            null, requestId, result);
        audit.recordCurrent("AI_FACE_CAPTURE", "GATE_EVIDENCE", created.evidenceId(), "SUCCESS", null, created.evidenceId());
        return response(created.responseBody(), requestId);
    }

    private static String requestId(String supplied) {
        if (supplied != null && supplied.matches("[A-Za-z0-9._:-]{1,128}")) return supplied;
        return UUID.randomUUID().toString();
    }

    private static ResponseEntity<byte[]> response(byte[] body, String requestId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .header("X-Request-ID", requestId).body(body);
    }

    public record FaceCameraRequest(Long familyMemberId, int cameraIndex, String cameraSource, EvidenceOperation operation) { }
    public record GuestFaceCameraRequest(String vehicleEvidenceId, int cameraIndex, String cameraSource) { }
    public record CameraCaptureRequest(int cameraIndex, String cameraSource, EvidenceOperation operation) { }
    public record RecognitionCameraRequest(int cameraIndex, String cameraSource, int frameCount, EvidenceOperation operation) { }
}
