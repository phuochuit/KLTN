package vn.edu.parking.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateEvidenceStorageTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void storesOpaqueImagesOutsideTheStaticUploadRoot() throws Exception {
        Path uploadRoot = temporaryDirectory.resolve("private-media");
        GateEvidenceStorage storage = new GateEvidenceStorage(
            temporaryDirectory.resolve("gate-evidence").toString(), uploadRoot.toString());
        byte[] image = {1, 2, 3};

        String reference = storage.store(image, 10);

        assertTrue(reference.matches("[0-9a-f-]{36}\\.bin"));
        assertFalse(storage.rootPath().startsWith(uploadRoot));
        assertArrayEquals(image, storage.read(reference, 10));
    }

    @Test
    void rejectsEvidenceRootsThatOverlapStaticUploads() {
        Path uploadRoot = temporaryDirectory.resolve("private-media");
        assertThrows(IllegalArgumentException.class, () -> new GateEvidenceStorage(
            uploadRoot.resolve("gate-evidence").toString(), uploadRoot.toString()));
        assertThrows(IllegalArgumentException.class, () -> new GateEvidenceStorage(
            temporaryDirectory.toString(), uploadRoot.toString()));
    }

    @Test
    void boundsReadsAndRejectsPathTraversal() throws Exception {
        GateEvidenceStorage storage = storage();
        String reference = storage.store(new byte[]{1, 2, 3, 4, 5}, 10);

        assertThrows(IOException.class, () -> storage.read(reference, 4));
        assertThrows(IllegalArgumentException.class, () -> storage.read("..\\secret.bin", 10));
    }

    @Test
    void deletesOnlyOldUnreferencedGateFiles() throws Exception {
        GateEvidenceStorage storage = storage();
        String orphan = storage.store(new byte[]{1}, 10);
        String referenced = storage.store(new byte[]{2}, 10);
        Instant cutoff = Instant.now().minus(2, ChronoUnit.DAYS);
        Files.setLastModifiedTime(storage.rootPath().resolve(orphan), FileTime.from(cutoff));
        Files.setLastModifiedTime(storage.rootPath().resolve(referenced), FileTime.from(cutoff));
        String youngOrphan = UUID.randomUUID() + ".bin";
        Files.write(storage.rootPath().resolve(youngOrphan), new byte[]{3});

        int deleted = storage.deleteOrphans(cutoff.plus(1, ChronoUnit.DAYS),
            name -> name.equals(referenced));

        assertEquals(1, deleted);
        assertFalse(Files.exists(storage.rootPath().resolve(orphan)));
        assertTrue(Files.exists(storage.rootPath().resolve(referenced)));
        assertTrue(Files.exists(storage.rootPath().resolve(youngOrphan)));
    }

    private GateEvidenceStorage storage() throws IOException {
        return new GateEvidenceStorage(temporaryDirectory.resolve("gate-evidence").toString(),
            temporaryDirectory.resolve("private-media").toString());
    }
}
