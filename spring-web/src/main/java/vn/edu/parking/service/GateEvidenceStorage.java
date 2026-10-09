package vn.edu.parking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Service
public class GateEvidenceStorage {
    private static final Pattern STORED_FILE = Pattern.compile(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(?:bin|tmp)");
    private final Path root;

    public GateEvidenceStorage(
            @Value("${parking.gate-evidence.directory:var/private-gate-evidence}") String evidenceDir,
            @Value("${parking.upload-dir:var/private-media}") String uploadDir) throws IOException {
        Path configuredEvidenceRoot = Paths.get(evidenceDir).toAbsolutePath().normalize();
        Path configuredUploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (configuredEvidenceRoot.startsWith(configuredUploadRoot)
                || configuredUploadRoot.startsWith(configuredEvidenceRoot)) {
            throw new IllegalArgumentException("Gate evidence storage must not overlap the static upload root");
        }
        Files.createDirectories(configuredUploadRoot);
        Files.createDirectories(configuredEvidenceRoot);

        Path realEvidenceRoot = configuredEvidenceRoot.toRealPath();
        Path realUploadRoot = configuredUploadRoot.toRealPath();
        if (realEvidenceRoot.startsWith(realUploadRoot) || realUploadRoot.startsWith(realEvidenceRoot)) {
            throw new IllegalArgumentException("Gate evidence storage must not overlap the static upload root");
        }
        root = realEvidenceRoot;
    }

    public String store(byte[] bytes, int maxBytes) throws IOException {
        if (bytes == null || bytes.length == 0 || maxBytes < 1 || bytes.length > maxBytes)
            throw new IllegalArgumentException("Gate evidence image is empty or exceeds its allowed size");

        String filename = UUID.randomUUID() + ".bin";
        Path target = resolve(filename);
        Path temporary = resolve(filename.substring(0, filename.length() - 4) + ".tmp");
        try {
            Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            return filename;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public byte[] read(String filename, int maxBytes) throws IOException {
        if (maxBytes < 1) throw new IllegalArgumentException("Maximum image size must be positive");
        Path image = resolve(filename);
        if (!Files.isRegularFile(image, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Gate evidence image is unavailable");
        Path realImage = image.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!realImage.startsWith(root) || !Files.isRegularFile(realImage, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Gate evidence image is unavailable");
        try (var input = Files.newInputStream(realImage, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) throw new IOException("Gate evidence image exceeds its allowed size");
            return bytes;
        }
    }

    public void delete(String filename) throws IOException {
        Files.deleteIfExists(resolve(filename));
    }

    public int deleteOrphans(Instant olderThan, Predicate<String> isReferenced) throws IOException {
        int deleted = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(root)) {
            for (Path file : files) {
                String filename = file.getFileName().toString();
                if (!STORED_FILE.matcher(filename).matches()
                        || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
                if (attributes.lastModifiedTime().toInstant().isAfter(olderThan) || isReferenced.test(filename)) continue;
                if (Files.deleteIfExists(file)) deleted++;
            }
        }
        return deleted;
    }

    Path rootPath() { return root; }

    private Path resolve(String filename) {
        if (filename == null || !STORED_FILE.matcher(filename).matches())
            throw new IllegalArgumentException("Invalid gate evidence reference");
        Path resolved = root.resolve(filename).normalize();
        if (!resolved.getParent().equals(root)) throw new IllegalArgumentException("Invalid gate evidence reference");
        return resolved;
    }
}
