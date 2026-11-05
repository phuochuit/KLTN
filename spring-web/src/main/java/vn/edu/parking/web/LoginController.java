package vn.edu.parking.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {
    @GetMapping("/login")
    String login(@RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            @RequestParam(required = false) String passwordChanged,
            @RequestParam(required = false) String throttled,
            @RequestParam(required = false) String ended,
            @RequestParam(required = false) String expired,
            Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        if (error != null) model.addAttribute("errorMessage", "Sai tài khoản hoặc mật khẩu.");
        else if (throttled != null) model.addAttribute("errorMessage",
            "Đăng nhập sai quá nhiều lần. Vui lòng thử lại sau 15 phút.");
        else if (expired != null) model.addAttribute("errorMessage",
            "Tài khoản đã được đăng nhập trên thiết bị khác.");
        if (logout != null) model.addAttribute("successMessage", "Bạn đã đăng xuất an toàn.");
        else if (ended != null) model.addAttribute("successMessage",
            "Phiên làm việc đã được kết thúc. Vui lòng đăng nhập lại.");
        else if (passwordChanged != null) model.addAttribute("successMessage",
            "Đổi mật khẩu thành công. Vui lòng đăng nhập bằng mật khẩu mới.");
        return "login";
    }
}
