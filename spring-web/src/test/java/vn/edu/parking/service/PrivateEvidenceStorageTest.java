package vn.edu.parking.service;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.parking.domain.EvidenceAssetRole;

class PrivateEvidenceStorageTest {
    @TempDir Path temp;

    @Test
    void storesReadsHashesAndDeletesPrivateImage() throws Exception {
        var storage = new PrivateEvidenceStorage(temp.toString());
        byte[] image = png();
        var stored = storage.storeImage(UUID.randomUUID().toString(), EvidenceAssetRole.REALTIME_FACE, image);

        assertEquals("image/png", stored.mediaType());
        assertEquals(64, stored.sha256().length());
        assertArrayEquals(image, storage.read(stored.storageKey(), stored.sha256()));
        assertTrue(storage.delete(stored.storageKey()));
        assertFalse(storage.delete(stored.storageKey()));
    }

    @Test
    void rejectsPathTraversalAndNonImageContent() throws Exception {
        var storage = new PrivateEvidenceStorage(temp.toString());
        assertThrows(IllegalArgumentException.class, () -> storage.read("../secret.jpg", null));
        assertThrows(IllegalArgumentException.class, () -> storage.storeImage(
            UUID.randomUUID().toString(), EvidenceAssetRole.REALTIME_FACE, "not-an-image".getBytes()));
        assertEquals(0, Files.walk(temp).filter(Files::isRegularFile).count());
    }

    @Test
    void detectsTamperedFile() throws Exception {
        var storage = new PrivateEvidenceStorage(temp.toString());
        var stored = storage.storeImage(UUID.randomUUID().toString(), EvidenceAssetRole.ANNOTATED_VEHICLE, png());
        Path file = temp.resolve(stored.storageKey());
        Files.write(file, pngWithColor(0x00ff00));
        assertThrows(IllegalStateException.class, () -> storage.read(stored.storageKey(), stored.sha256()));
    }

    private static byte[] png() throws Exception { return pngWithColor(0xff0000); }
    private static byte[] pngWithColor(int rgb) throws Exception {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, rgb);
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } finally { image.flush(); }
    }
}
