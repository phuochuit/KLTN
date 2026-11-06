package vn.edu.parking.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import vn.edu.parking.service.ManagementWriteConflictException;

@ControllerAdvice(assignableTypes = AdminController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ManagementCoreWriteExceptionHandler {
    private static final Map<String, String> FORM_PATHS = Map.of(
            "/households", "/households",
            "/members", "/households",
            "/vehicles", "/vehicles",
            "/registrations", "/registrations",
            "/cards", "/cards",
            "/settings", "/settings");

    @ExceptionHandler(IllegalArgumentException.class)
    ModelAndView invalidInput(IllegalArgumentException exception, HttpServletRequest request) {
        if (!isManagementWrite(request)) throw exception;
        return error(HttpStatus.BAD_REQUEST, safeMessage(exception), request, true);
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    ModelAndView invalidBinding(ServletRequestBindingException exception, HttpServletRequest request)
            throws ServletRequestBindingException {
        if (!isManagementWrite(request)) throw exception;
        return error(HttpStatus.BAD_REQUEST, "Một hoặc nhiều trường dữ liệu không hợp lệ.", request, true);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ModelAndView invalidType(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        if (!isManagementWrite(request)) throw exception;
        return error(HttpStatus.BAD_REQUEST, "Một hoặc nhiều trường dữ liệu không hợp lệ.", request, true);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ModelAndView uploadTooLarge(MaxUploadSizeExceededException exception, HttpServletRequest request) {
        if (!isManagementWrite(request)) throw exception;
        return error(HttpStatus.PAYLOAD_TOO_LARGE,
                "Ảnh tải lên vượt giới hạn cho phép. Dữ liệu chưa được lưu.", request, true);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ModelAndView integrityConflict(DataIntegrityViolationException exception, HttpServletRequest request) {
        if (!isManagementWrite(request)) throw exception;
        return error(HttpStatus.CONFLICT,
                "Dữ liệu đã thay đổi hoặc trùng với hồ sơ hiện có. Hãy tải lại và kiểm tra trước khi lưu lại.",
                request, true);
    }

    @ExceptionHandler(ManagementWriteConflictException.class)
    ModelAndView staleData(ManagementWriteConflictException exception, HttpServletRequest request) {
        if (!isManagementWrite(request)) throw exception;
        return error(HttpStatus.CONFLICT, exception.getMessage(), request, true);
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class, IllegalStateException.class})
    ModelAndView writeOutcomeUnknown(RuntimeException exception, HttpServletRequest request) {
        if (!isManagementWrite(request)) throw exception;
        return error(HttpStatus.SERVICE_UNAVAILABLE,
                "Không xác nhận được kết quả lưu. Hãy tải lại dữ liệu trước khi quyết định có gửi lại hay không.",
                request, false);
    }

    private boolean isManagementWrite(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return FORM_PATHS.containsKey(path) && "POST".equalsIgnoreCase(request.getMethod());
    }

    private ModelAndView error(HttpStatus status, String message, HttpServletRequest request, boolean retrySafe) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        ModelAndView result = new ModelAndView("management-write-error", status);
        result.addObject("message", message);
        result.addObject("returnPath", FORM_PATHS.get(path));
        result.addObject("retrySafe", retrySafe);
        return result;
    }

    private String safeMessage(IllegalArgumentException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Dữ liệu gửi lên không hợp lệ." : message;
    }
}
