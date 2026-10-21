package vn.edu.parking.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.parking.domain.*;
import vn.edu.parking.repository.GateEvidenceAssetRepository;
import vn.edu.parking.repository.GateEvidenceRepository;
import vn.edu.parking.service.PrivateEvidenceStorage;
import vn.edu.parking.service.SecurityAuditService;

class GateEvidenceAdminControllerTest {
    @TempDir Path temporaryDirectory;

    @Test
    void readsVerifiedPrivateAssetAndAuditsAccess() throws Exception {
        String evidenceId = "10000000-0000-0000-0000-000000000001";
        String assetId = "20000000-0000-0000-0000-000000000002";
        var storage = new PrivateEvidenceStorage(temporaryDirectory.toString());
        byte[] image = jpeg();
        var stored = storage.storeImage(evidenceId, EvidenceAssetRole.REALTIME_FACE, image);
        GateEvidenceAsset asset = mock(GateEvidenceAsset.class);
        when(asset.getStorageKey()).thenReturn(stored.storageKey());
        when(asset.getSha256()).thenReturn(stored.sha256());
        when(asset.getMediaType()).thenReturn(stored.mediaType());
        GateEvidenceRepository evidences = mock(GateEvidenceRepository.class);
        GateEvidenceAssetRepository assets = mock(GateEvidenceAssetRepository.class);
        SecurityAuditService audit = mock(SecurityAuditService.class);
        when(assets.findByIdAndEvidenceIdAndDeletedAtIsNull(assetId, evidenceId))
            .thenReturn(Optional.of(asset));
        var controller = new GateEvidenceAdminController(evidences, assets, storage, audit);

        var response = controller.asset(evidenceId, assetId);

        assertArrayEquals(image, response.getBody());
        assertEquals("image/jpeg", response.getHeaders().getContentType().toString());
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
        verify(audit).recordCurrent("GATE_EVIDENCE_ASSET_VIEW", "GATE_EVIDENCE_ASSET",
            assetId, "SUCCESS", null, evidenceId);
    }

    @Test
    void refusesAssetThatDoesNotBelongToEvidence() throws Exception {
        var assets = mock(GateEvidenceAssetRepository.class);
        var controller = new GateEvidenceAdminController(mock(GateEvidenceRepository.class), assets,
            new PrivateEvidenceStorage(temporaryDirectory.toString()), mock(SecurityAuditService.class));
        String evidenceId = "10000000-0000-0000-0000-000000000001";
        String assetId = "20000000-0000-0000-0000-000000000002";
        when(assets.findByIdAndEvidenceIdAndDeletedAtIsNull(assetId, evidenceId))
            .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> controller.asset(evidenceId, assetId));
    }

    private static byte[] jpeg() throws Exception {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpg", output);
            return output.toByteArray();
        } finally { image.flush(); }
    }
}
