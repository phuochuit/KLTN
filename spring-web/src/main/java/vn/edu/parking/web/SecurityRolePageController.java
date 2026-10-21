package vn.edu.parking.web;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.parking.domain.SecurityPermission;
import vn.edu.parking.repository.SecurityRoleRepository;
import vn.edu.parking.repository.SystemAccountRepository;
import vn.edu.parking.service.SecurityRoleService;

@Controller
public class SecurityRolePageController {
    private final SecurityRoleRepository roles;
    private final SystemAccountRepository accounts;
    private final SecurityRoleService service;

    public SecurityRolePageController(SecurityRoleRepository roles, SystemAccountRepository accounts,
            SecurityRoleService service) {
        this.roles = roles;
        this.accounts = accounts;
        this.service = service;
    }

    @GetMapping("/roles")
    String page(Model model) {
        model.addAttribute("roles", roles.findAllByOrderByDisplayNameAsc().stream()
            .map(role -> new RoleView(role, accounts.countBySecurityRole_Id(role.getId()))).toList());
        model.addAttribute("permissionOptions", permissionOptions());
        return "security-roles";
    }

    @PostMapping("/roles/create")
    String create(@RequestParam String code, @RequestParam String displayName,
            @RequestParam String employeePrefix, @RequestParam(required = false) String description,
            @RequestParam(required = false) Set<SecurityPermission> permissions,
            RedirectAttributes redirect) {
        try {
            service.create(code, displayName, employeePrefix, description, permissions);
            redirect.addFlashAttribute("message", "Đã tạo vai trò mới.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/roles";
    }

    @PostMapping("/roles/update")
    String update(@RequestParam Long id, @RequestParam String displayName,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "false") boolean enabled,
            @RequestParam(required = false) Set<SecurityPermission> permissions,
            RedirectAttributes redirect) {
        try {
            service.update(id, displayName, description, enabled, permissions);
            redirect.addFlashAttribute("message", "Đã cập nhật vai trò. Các tài khoản liên quan cần đăng nhập lại.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/roles";
    }

    private static List<PermissionOption> permissionOptions() {
        return List.of(
            option(SecurityPermission.DASHBOARD_VIEW, "Xem tổng quan", "Xem màn hình tổng quan bãi xe."),
            option(SecurityPermission.ACCOUNT_MANAGE, "Quản lý tài khoản", "Tạo, cập nhật, khóa và đặt lại mật khẩu."),
            option(SecurityPermission.ROLE_MANAGE, "Quản lý vai trò", "Tạo vai trò và thay đổi quyền hạn."),
            option(SecurityPermission.SHIFT_MANAGE, "Phân ca", "Tạo và xóa lịch làm việc của nhân viên."),
            option(SecurityPermission.RESIDENT_MANAGE, "Quản lý cư dân", "Quản lý hộ gia đình và hồ sơ thành viên."),
            option(SecurityPermission.VEHICLE_MANAGE, "Quản lý phương tiện", "Đăng ký và cập nhật phương tiện."),
            option(SecurityPermission.CARD_MANAGE, "Quản lý thẻ", "Cấp và quản lý thẻ gửi xe."),
            option(SecurityPermission.PRICING_MANAGE, "Quản lý bảng giá", "Thay đổi mức phí gửi xe."),
            option(SecurityPermission.SLOT_MANAGE, "Quản lý ô đỗ", "Cấu hình và điều phối ô đỗ."),
            option(SecurityPermission.HISTORY_VIEW, "Xem lịch sử và doanh thu", "Tra cứu lượt xe và báo cáo doanh thu."),
            option(SecurityPermission.PAYMENT_RECONCILE, "Đối soát giao dịch", "Xem giao dịch, kiểm tra và chốt đối soát theo ca."),
            option(SecurityPermission.SECURITY_AUDIT_VIEW, "Xem nhật ký bảo mật", "Kiểm tra hoạt động bảo mật và xử lý ngoại lệ."),
            option(SecurityPermission.PRIVATE_EVIDENCE_VIEW, "Xem ảnh xác minh riêng tư", "Xem ảnh khuôn mặt và ảnh xác minh được bảo vệ."),
            option(SecurityPermission.GATE_OPERATE, "Vận hành trạm gác", "Nhận dạng và xử lý xe vào, xe ra."),
            option(SecurityPermission.AI_RESULT_CORRECT, "Chỉnh kết quả nhận dạng", "Chỉnh kết quả AI khi có ca trực và đủ dữ liệu kiểm tra.")
        );
    }

    private static PermissionOption option(SecurityPermission permission, String label, String description) {
        return new PermissionOption(permission, label, description);
    }

    public record RoleView(vn.edu.parking.domain.SecurityRole role, long accountCount) { }
    public record PermissionOption(SecurityPermission permission, String label, String description) { }
}
