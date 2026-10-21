package vn.edu.parking.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResidentImageStorageTest {
    @TempDir java.nio.file.Path temporaryDirectory;

    @Test
    void decodesAndReencodesUploadedImageAsRealJpeg() throws Exception {
        var source = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0x80ff0000);
        byte[] png;
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(source, "png", output);
            png = output.toByteArray();
        }
        var storage = new ResidentImageStorage(temporaryDirectory.toString());

        String reference = storage.save(new MockMultipartFile("face", "face.png", "image/png", png), null);

        assertTrue(reference.matches("resident:[0-9a-f-]{36}\\.jpg"));
        String filename = reference.substring("resident:".length());
        byte[] stored = storage.readResidentImage(filename);
        assertEquals((byte) 0xff, stored[0]);
        assertEquals((byte) 0xd8, stored[1]);
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(stored)));
    }

    @Test
    void rejectsInvalidContentAndUnsafeFilename() throws Exception {
        var storage = new ResidentImageStorage(temporaryDirectory.toString());
        assertThrows(IllegalArgumentException.class, () -> storage.save(
            new MockMultipartFile("face", "fake.jpg", "image/jpeg", "not-an-image".getBytes()), null));
        assertThrows(IllegalArgumentException.class, () -> storage.readResidentImage("../secret.jpg"));
        assertThrows(IllegalArgumentException.class, () -> storage.readResidentImage("not-a-uuid.jpg"));
    }

    @Test
    void readsLosslessPrivatePngReference() throws Exception {
        var storage = new ResidentImageStorage(temporaryDirectory.toString());
        String filename = "123e4567-e89b-42d3-a456-426614174000.png";
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1};
        Files.write(storage.getPrivateRoot().resolve(filename), png);
        assertArrayEquals(png, storage.readStoredReference("resident:" + filename));
    }

    @Test
    void refusesSymbolicLinkWhenPlatformSupportsIt() throws Exception {
        var storage = new ResidentImageStorage(temporaryDirectory.toString());
        var target = temporaryDirectory.resolve("outside.jpg");
        Files.write(target, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9});
        var residents = temporaryDirectory.resolve("residents");
        var link = residents.resolve("123e4567-e89b-42d3-a456-426614174000.jpg");
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException ex) {
            return;
        }
        assertThrows(IllegalArgumentException.class,
            () -> storage.readResidentImage(link.getFileName().toString()));
    }
}
