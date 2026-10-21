package vn.edu.parking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ResidentImageStorage {
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_DIMENSION = 8192;
    private static final long MAX_PIXELS = 40_000_000L;
    private static final Pattern STORED_FILENAME = Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.(jpg|png)",
        Pattern.CASE_INSENSITIVE);
    private static final String PRIVATE_PREFIX = "resident:";
    private final Path root;
    private final Path legacyRoot;

    @Autowired
    public ResidentImageStorage(
            @Value("${parking.private-storage-path:private-data}") String privateStoragePath,
            @Value("${parking.upload-dir:uploads}") String uploadDir) throws IOException {
        root = Paths.get(privateStoragePath).toAbsolutePath().normalize().resolve("residents");
        legacyRoot = Paths.get(uploadDir).toAbsolutePath().normalize().resolve("residents");
        Files.createDirectories(root);
    }

    ResidentImageStorage(String privateStoragePath) throws IOException {
        root = Paths.get(privateStoragePath).toAbsolutePath().normalize().resolve("residents");
        legacyRoot = root;
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
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
            if (decoded == null)
                throw new IllegalArgumentException("Tệp khuôn mặt không phải ảnh hợp lệ");
            if (decoded.getWidth() <= 0 || decoded.getHeight() <= 0
                    || decoded.getWidth() > MAX_DIMENSION || decoded.getHeight() > MAX_DIMENSION
                    || (long) decoded.getWidth() * decoded.getHeight() > MAX_PIXELS)
                throw new IllegalArgumentException("Kích thước ảnh khuôn mặt không hợp lệ");

            BufferedImage rgb = new BufferedImage(decoded.getWidth(), decoded.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = rgb.createGraphics();
            try {
                graphics.drawImage(decoded, 0, 0, java.awt.Color.WHITE, null);
            } finally {
                graphics.dispose();
                decoded.flush();
            }
            byte[] jpeg;
            try (var output = new ByteArrayOutputStream()) {
                if (!ImageIO.write(rgb, "jpg", output))
                    throw new IllegalStateException("Không có bộ mã hóa JPEG");
                jpeg = output.toByteArray();
            } finally {
                rgb.flush();
            }
            if (jpeg.length > MAX_IMAGE_BYTES)
                throw new IllegalArgumentException("Ảnh khuôn mặt sau xử lý vượt quá 10 MB");

            String filename = UUID.randomUUID() + ".jpg";
            Path destination = safePath(filename);
            Path temporary = Files.createTempFile(root, ".resident-", ".tmp");
            try {
                Files.write(temporary, jpeg, StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(temporary, destination);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            return PRIVATE_PREFIX + filename;
        } catch (IllegalArgumentException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalStateException("Không thể lưu ảnh khuôn mặt: " + ex.getMessage(), ex); }
    }

    public String saveCaptured(String capturedDataUrl) { return save(null, capturedDataUrl); }

    public byte[] readResidentImage(String filename) {
        return readFromRoot(root, filename);
    }

    private byte[] readFromRoot(Path storageRoot, String filename) {
        try {
            Path file = safePath(storageRoot, filename);
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                throw new IllegalArgumentException("Không tìm thấy ảnh cư dân");
            long size = Files.size(file);
            if (size <= 0 || size > MAX_IMAGE_BYTES)
                throw new IllegalArgumentException("Kích thước ảnh cư dân không hợp lệ");
            return Files.readAllBytes(file);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new IllegalStateException("Không thể đọc ảnh cư dân", ex);
        }
    }

    public byte[] readStoredReference(String reference) {
        if (reference == null || reference.isBlank())
            throw new IllegalArgumentException("Thành viên chưa có ảnh đăng ký");
        String normalized = reference.replace('\\', '/').trim();
        boolean privateReference = normalized.startsWith(PRIVATE_PREFIX);
        if (privateReference) normalized = normalized.substring(PRIVATE_PREFIX.length());
        int slash = normalized.lastIndexOf('/');
        String filename = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return readFromRoot(privateReference ? root : legacyRoot, filename);
    }

    private Path safePath(String filename) {
        return safePath(root, filename);
    }

    private Path safePath(Path storageRoot, String filename) {
        if (filename == null || !STORED_FILENAME.matcher(filename).matches())
            throw new IllegalArgumentException("Tên ảnh cư dân không hợp lệ");
        Path path = storageRoot.resolve(filename).normalize();
        if (!path.getParent().equals(storageRoot))
            throw new IllegalArgumentException("Đường dẫn ảnh cư dân không hợp lệ");
        return path;
    }

    public Path getPrivateRoot() { return root; }
}
