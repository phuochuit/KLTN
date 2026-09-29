package vn.edu.parking.service;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class CccdQrService {
    public record CccdQrResult(String raw, String citizenId, String oldCitizenId, String fullName,
                               LocalDate dateOfBirth, String gender, String permanentAddress,
                               LocalDate issueDate) { }

    public CccdQrResult scan(MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) throw new IllegalArgumentException("Hãy chọn ảnh có mã QR trên CCCD");
            if (file.getSize() > 10 * 1024 * 1024) throw new IllegalArgumentException("Ảnh QR vượt quá 10 MB");
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) throw new IllegalArgumentException("Tệp đã chọn không phải hình ảnh hợp lệ");
            var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));
            hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
            String raw = new MultiFormatReader().decode(bitmap, hints).getText();
            String[] parts = raw.split("\\|", -1);
            if (parts.length < 7) throw new IllegalArgumentException("QR đọc được nhưng không đúng cấu trúc dữ liệu CCCD Việt Nam");
            return new CccdQrResult(raw, digits(parts[0]), digits(parts[1]), parts[2].trim(),
                parseDate(parts[3], "ngày sinh"), parts[4].trim(), parts[5].trim(), parseDate(parts[6], "ngày cấp"));
        } catch (NotFoundException ex) {
            throw new IllegalArgumentException("Không tìm thấy mã QR rõ ràng. Hãy chụp gần hơn, lấy nét và tránh phản sáng");
        } catch (IllegalArgumentException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalStateException("Không thể đọc ảnh QR: " + ex.getMessage(), ex); }
    }

    private String digits(String value) { return value == null ? "" : value.replaceAll("\\D", ""); }
    private LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDate.parse(value.trim(), DateTimeFormatter.ofPattern("ddMMyyyy")); }
        catch (DateTimeParseException ex) { throw new IllegalArgumentException("QR có " + field + " không hợp lệ"); }
    }
}
