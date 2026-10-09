package vn.edu.parking.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResidentImageStorageTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsStoredRegistrationImageByItsOpaqueReference() throws Exception {
        ResidentImageStorage storage = new ResidentImageStorage(temporaryDirectory.toString());
        byte[] image = validPng();
        String reference = storage.save(new MockMultipartFile("image", "face.png", "image/png", image), null);

        assertArrayEquals(image, storage.readResidentImage(reference));
    }

    @Test
    void rejectsReferencesOutsideTheResidentImageNamespace() throws Exception {
        ResidentImageStorage storage = new ResidentImageStorage(temporaryDirectory.toString());

        assertThrows(IllegalArgumentException.class,
            () -> storage.readResidentImage("/uploads/residents/../../application.yml"));
        assertThrows(IllegalArgumentException.class,
            () -> storage.readResidentImage("/uploads/cccd/000000000000.jpg"));
        assertThrows(IllegalArgumentException.class,
            () -> storage.readResidentImage("C:\\private\\resident.jpg"));
    }

    @Test
    void rejectsMissingRegistrationImageWithoutReturningItsPath() throws Exception {
        ResidentImageStorage storage = new ResidentImageStorage(temporaryDirectory.toString());
        String reference = "/uploads/residents/00000000-0000-0000-0000-000000000000.jpg";

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> storage.readResidentImage(reference));
        assertTrue(error.getMessage().contains("unavailable"));
    }

    @Test
    void boundsBytesReadFromStoredImage() throws Exception {
        ResidentImageStorage storage = new ResidentImageStorage(temporaryDirectory.toString());
        String filename = UUID.randomUUID() + ".jpg";
        Path storedImage = storage.getUploadRoot().resolve("residents").resolve(filename);
        Files.write(storedImage, new byte[10 * 1024 * 1024 + 1]);

        assertThrows(IllegalStateException.class,
            () -> storage.readResidentImage("/uploads/residents/" + filename));
    }

    private static byte[] validPng() throws IOException {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }
}
