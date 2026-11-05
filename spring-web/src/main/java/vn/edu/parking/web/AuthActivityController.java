package vn.edu.parking.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.parking.service.AuthActivityService;

@RestController
public class AuthActivityController {
    private final AuthActivityService activity;

    public AuthActivityController(AuthActivityService activity) {
        this.activity = activity;
    }

    /**
     * Không truyền afterId: trả về mốc hiện tại và danh sách rỗng, dùng khi mở trang.
     * Có afterId: trả về các sự kiện mới hơn mốc đó để Web hiện thông báo.
     */
    @GetMapping("/auth-activity")
    ResponseEntity<AuthActivityService.Feed> feed(@RequestParam(required = false) Long afterId) {
        return ResponseEntity.ok(afterId == null ? activity.latest() : activity.since(afterId));
    }
}
