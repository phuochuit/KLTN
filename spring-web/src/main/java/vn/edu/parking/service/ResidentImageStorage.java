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

@Service
public class ResidentImageStorage {
    private final Path root;

    public ResidentImageStorage(@Value("${parking.upload-dir:uploads}") String uploadDir) throws IOException {
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
            if (bytes.length > 10 * 1024 * 1024) throw new IllegalArgumentException("Ảnh khuôn mặt vượt quá 10 MB");
            if (ImageIO.read(new ByteArrayInputStream(bytes)) == null)
                throw new IllegalArgumentException("Tệp khuôn mặt không phải ảnh hợp lệ");
            String filename = UUID.randomUUID() + ".jpg";
            Files.write(root.resolve(filename), bytes, StandardOpenOption.CREATE_NEW);
            return "/uploads/residents/" + filename;
        } catch (IllegalArgumentException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalStateException("Không thể lưu ảnh khuôn mặt: " + ex.getMessage(), ex); }
    }

    public String saveCaptured(String capturedDataUrl) { return save(null, capturedDataUrl); }

    public Path getUploadRoot() { return root.getParent(); }
}
