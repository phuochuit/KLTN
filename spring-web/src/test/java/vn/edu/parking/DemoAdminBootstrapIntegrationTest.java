package vn.edu.parking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import vn.edu.parking.domain.AccountRole;
import vn.edu.parking.domain.SystemAccount;
import vn.edu.parking.repository.SystemAccountRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Xác nhận seed tài khoản mẫu chạy đúng thứ tự với SecurityRoleBootstrap: vai trò phải có trước
 * thì mới gán được, và mã nhân viên phải được cấp theo tiền tố của vai trò.
 */
@SpringBootTest(properties = {
    "parking.security.bootstrap.username=",
    "parking.security.bootstrap.password=",
    "parking.security.bootstrap.demo-password=synthetic demo secret"
})
class DemoAdminBootstrapIntegrationTest {

    @Autowired
    private SystemAccountRepository accounts;

    @Test
    void seedsAdminAccountWithManagementRoleWhenDatabaseIsEmpty() {
        SystemAccount admin = accounts.findByUsername("admin").orElseThrow();

        assertEquals(AccountRole.MANAGEMENT, admin.getRole());
        assertNotNull(admin.getSecurityRole(), "phải gán vai trò bảo mật thì mới đăng nhập được");
        assertNotNull(admin.getEmployeeCode(), "mã nhân viên phải được cấp theo tiền tố vai trò");
        assertTrue(admin.isMustChangePassword(), "mật khẩu mẫu phải bị buộc đổi ở lần đăng nhập đầu");
        assertTrue(admin.isEnabled());
    }
}
