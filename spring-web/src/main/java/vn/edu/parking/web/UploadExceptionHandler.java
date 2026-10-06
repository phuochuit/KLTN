package vn.edu.parking.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class UploadExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ModelAndView handleUploadTooLarge() {
        ModelAndView result = new ModelAndView("upload-error", HttpStatus.PAYLOAD_TOO_LARGE);
        result.addObject("message",
            "Ảnh tải lên quá lớn. Mỗi ảnh tối đa 10 MB và tổng dữ liệu của một lần lưu tối đa 25 MB.");
        return result;
    }
}
