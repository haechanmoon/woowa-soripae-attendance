package com.woowasoripae.attendance.web.admin;

import com.woowasoripae.attendance.web.admin.dto.AdminPasswordRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * MVP shortcut matching the current frontend: one shared password gates the "임원진" tab client-side,
 * this endpoint just confirms the password server-side so it isn't trivially bypassed by reading the JS.
 * It does NOT issue a session/token, so approve/reject/face-check below stay unauthenticated after this
 * check passes. Fine for a small trusted club for the MVP deadline, but flag before wider rollout:
 * add a short-lived token from this endpoint and require it on the admin-only endpoints.
 *
 * <p>The password has no default and must be supplied via the {@code ADMIN_PASSWORD} environment
 * variable; when it is missing this endpoint rejects every attempt rather than letting anyone in.
 */
@RestController
public class AdminAuthController {

    private final AdminProperties adminProperties;

    public AdminAuthController(AdminProperties adminProperties) {
        this.adminProperties = adminProperties;
    }

    @PostMapping("/api/admin/auth")
    public ResponseEntity<Void> verifyPassword(@Valid @RequestBody AdminPasswordRequest request) {
        // 비밀번호가 설정되지 않은 채 떠 있으면 아무도 통과시키지 않는다. 빈 값을 "누구나 통과"로
        // 해석하면 ADMIN_PASSWORD를 빠뜨린 배포가 그 순간 무인증 상태가 되기 때문이다.
        if (!StringUtils.hasText(adminProperties.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (adminProperties.password().equals(request.password())) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
