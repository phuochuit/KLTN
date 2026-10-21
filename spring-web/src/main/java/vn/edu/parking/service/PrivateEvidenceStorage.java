package vn.edu.parking.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.edu.parking.domain.EvidenceAssetRole;

@Service
public class PrivateEvidenceStorage {
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_DIMENSION = 8192;
    private static final long MAX_PIXELS = 40_000_000L;
    private static final Pattern STORAGE_KEY = Pattern.compile(
        "evidence/[0-9a-fA-F-]{36}/[A-Z_]+/[0-9a-fA-F-]{36}\\.(jpg|png)");
    private final Path root;

    public PrivateEvidenceStorage(@Value("${parking.private-storage-path:private-data}") String path) throws IOException {
        root = Paths.get(path).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public StoredAsset storeImage(String evidenceId, EvidenceAssetRole role, byte[] bytes) {
        if (evidenceId == null || role == null || bytes == null)
            throw new IllegalArgumentException("Thiếu dữ liệu ảnh evidence");
        try { UUID.fromString(evidenceId); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Evidence ID không hợp lệ", ex); }
        if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES)
            throw new IllegalArgumentException("Kích thước ảnh evidence không hợp lệ");

        ImageInfo image = inspect(bytes);
        String filename = UUID.randomUUID() + image.extension();
        String key = "evidence/" + evidenceId.toLowerCase(Locale.ROOT) + "/" + role.name() + "/" + filename;
        Path destination = safePath(key);
        try {
            Files.createDirectories(destination.getParent());
            Path temporary = Files.createTempFile(destination.getParent(), ".evidence-", ".tmp");
            try {
                Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(temporary, destination);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            return new StoredAsset(key, sha256(bytes), image.mediaType(), bytes.length,
                image.width(), image.height());
        } catch (IOException ex) {
            throw new IllegalStateException("Không thể lưu ảnh evidence", ex);
        }
    }

    public byte[] read(String storageKey, String expectedSha256) {
        Path file = safePath(storageKey);
        try {
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                throw new IllegalArgumentException("Không tìm thấy ảnh evidence");
            long size = Files.size(file);
            if (size <= 0 || size > MAX_IMAGE_BYTES)
                throw new IllegalArgumentException("Kích thước ảnh evidence không hợp lệ");
            byte[] bytes = Files.readAllBytes(file);
            if (expectedSha256 != null && !MessageDigest.isEqual(
                    sha256(bytes).getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    expectedSha256.toLowerCase(Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                throw new IllegalStateException("Hash ảnh evidence không khớp");
            return bytes;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new IllegalStateException("Không thể đọc ảnh evidence", ex);
        }
    }

    public boolean delete(String storageKey) {
        try { return Files.deleteIfExists(safePath(storageKey)); }
        catch (IOException ex) { throw new IllegalStateException("Không thể xóa ảnh evidence", ex); }
    }

    private ImageInfo inspect(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) throw new IllegalArgumentException("Evidence không phải ảnh hợp lệ");
            try {
                int width = image.getWidth(), height = image.getHeight();
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS)
                    throw new IllegalArgumentException("Kích thước pixel evidence không hợp lệ");
                boolean png = bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50
                    && bytes[2] == 0x4e && bytes[3] == 0x47;
                boolean jpeg = bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8
                    && bytes[2] == (byte) 0xff;
                if (!png && !jpeg) throw new IllegalArgumentException("Chỉ hỗ trợ evidence JPEG hoặc PNG");
                return new ImageInfo(png ? "image/png" : "image/jpeg", png ? ".png" : ".jpg", width, height);
            } finally { image.flush(); }
        } catch (IOException ex) {
            throw new IllegalArgumentException("Không thể giải mã ảnh evidence", ex);
        }
    }

    private Path safePath(String storageKey) {
        if (storageKey == null || !STORAGE_KEY.matcher(storageKey).matches())
            throw new IllegalArgumentException("Storage key evidence không hợp lệ");
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Đường dẫn evidence không hợp lệ");
        return path;
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 không khả dụng", ex); }
    }

    public Path getRoot() { return root; }
    public record StoredAsset(String storageKey, String sha256, String mediaType,
        long sizeBytes, int width, int height) { }
    private record ImageInfo(String mediaType, String extension, int width, int height) { }
}
