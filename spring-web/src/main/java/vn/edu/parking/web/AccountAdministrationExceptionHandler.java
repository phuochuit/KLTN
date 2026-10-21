package vn.edu.parking.web;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.edu.parking.service.AccountNotFoundException;

@RestControllerAdvice(assignableTypes = AccountAdministrationController.class)
public class AccountAdministrationExceptionHandler {
    @ExceptionHandler(AccountNotFoundException.class)
    ResponseEntity<Map<String, String>> accountNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "account_not_found"));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<Map<String, String>> invalidRequest() {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid_request"));
    }
}
