package vn.edu.parking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ResidentImageStorage {
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final String RESIDENT_REFERENCE_PREFIX = "/uploads/residents/";
    private static final Pattern RESIDENT_IMAGE_NAME = Pattern.compile(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.jpg");
    private final Path root;

    public ResidentImageStorage(@Value("${parking.upload-dir:var/private-media}") String uploadDir) throws IOException {
        root = Paths.get(uploadDir).toAbsolutePath().normalize().resolve("residents");
        Files.createDirectories(root);
    }

    public String save(MultipartFile upload, String capturedDataUrl) {
        try {
            byte[] bytes = null;
            if (upload != null && !upload.isEmpty()) bytes = upload.getBytes();
            else if (capturedDataUrl != null && !capturedDataUrl.isBlank()) {
                int comma = capturedDataUrl.indexOf(',');
                bytes = Base64.getDecoder().decode(comma >= 0 ? capturedDataUrl.substring(comma + 1) : capturedDataUrl);
            }
            if (bytes == null) return null;
            if (bytes.length > MAX_IMAGE_BYTES) throw new IllegalArgumentException("Ảnh khuôn mặt vượt quá 10 MB");
            if (ImageIO.read(new ByteArrayInputStream(bytes)) == null)
                throw new IllegalArgumentException("Tệp khuôn mặt không phải ảnh hợp lệ");
            String filename = UUID.randomUUID() + ".jpg";
            Files.write(root.resolve(filename), bytes, StandardOpenOption.CREATE_NEW);
            return RESIDENT_REFERENCE_PREFIX + filename;
        } catch (IllegalArgumentException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalStateException("Không thể lưu ảnh khuôn mặt: " + ex.getMessage(), ex); }
    }

    public String saveCaptured(String capturedDataUrl) { return save(null, capturedDataUrl); }

    public byte[] readResidentImage(String imageReference) {
        if (imageReference == null || !imageReference.startsWith(RESIDENT_REFERENCE_PREFIX)) {
            throw new IllegalArgumentException("Invalid resident image reference");
        }
        String filename = imageReference.substring(RESIDENT_REFERENCE_PREFIX.length());
        if (!RESIDENT_IMAGE_NAME.matcher(filename).matches()) {
            throw new IllegalArgumentException("Invalid resident image reference");
        }

        Path image = root.resolve(filename).normalize();
        if (!image.startsWith(root)) throw new IllegalArgumentException("Invalid resident image reference");
        try {
            Path realRoot = root.toRealPath();
            if (!Files.isRegularFile(image, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("Resident image is unavailable");
            }
            Path realImage = image.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!realImage.startsWith(realRoot) || !Files.isRegularFile(realImage, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("Resident image is unavailable");
            }
            try (var input = Files.newInputStream(realImage, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = input.readNBytes(MAX_IMAGE_BYTES + 1);
                if (bytes.length > MAX_IMAGE_BYTES
                        || ImageIO.read(new ByteArrayInputStream(bytes)) == null) {
                    throw new IllegalStateException("Resident image is unavailable");
                }
                return bytes;
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Resident image is unavailable");
        }
    }

    public Path getUploadRoot() { return root.getParent(); }
}
