package vn.edu.parking.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.edu.parking.service.AccountPasswordService;
import vn.edu.parking.service.InvalidCurrentPasswordException;

@Controller
public class AccountController {
    private final AccountPasswordService passwordService;
    public AccountController(AccountPasswordService passwordService) { this.passwordService = passwordService; }

    @GetMapping("/account/password")
    String passwordForm() { return "account-password"; }

    @PostMapping("/account/password")
    String changePassword(Authentication authentication, HttpServletRequest request,
            @RequestParam String currentPassword, @RequestParam String newPassword,
            @RequestParam String confirmPassword, Model model) {
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "Mật khẩu xác nhận không khớp.");
            return "account-password";
        }
        try {
            passwordService.changeOwnPassword(authentication.getName(), currentPassword, newPassword);
        } catch (InvalidCurrentPasswordException ex) {
            model.addAttribute("error", "Mật khẩu hiện tại không chính xác.");
            return "account-password";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", "Mật khẩu mới phải có ít nhất 12 đơn vị UTF-16 và tối đa 72 byte UTF-8.");
            return "account-password";
        } catch (DataAccessException | TransactionException ex) {
            model.addAttribute("error", "Không thể xác nhận kết quả đổi mật khẩu. Hãy thử đăng nhập lại trước khi gửi lại.");
            return "account-password";
        }
        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        return "redirect:/login?passwordChanged";
    }
}
