package vn.edu.parking.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.edu.parking.service.AccountPasswordService;
import vn.edu.parking.service.InvalidCurrentPasswordException;

@Controller
public class AccountController {
    private final AccountPasswordService passwordService;

    public AccountController(AccountPasswordService passwordService) {
        this.passwordService = passwordService;
    }

    @GetMapping("/account/password")
    String passwordForm() {
        return "account-password";
    }

    @PostMapping("/account/password")
    String changePassword(Authentication authentication, HttpServletRequest request,
            @RequestParam String currentPassword, @RequestParam String newPassword,
            @RequestParam String confirmPassword, Model model) {
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "New password entries do not match.");
            return "account-password";
        }

        try {
            passwordService.changeOwnPassword(authentication.getName(), currentPassword, newPassword);
        } catch (InvalidCurrentPasswordException ex) {
            model.addAttribute("error", "Current password is incorrect.");
            return "account-password";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", "Use a password between 12 and 72 UTF-8 bytes.");
            return "account-password";
        }

        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        return "redirect:/login?passwordChanged";
    }
}
