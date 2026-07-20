package com.woowasoripae.attendance.web.admin;

import com.woowasoripae.attendance.web.admin.dto.AdminPasswordRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * MVP shortcut matching the current frontend: one shared password gates the "임원진" tab client-side,
 * this endpoint just confirms the password server-side so it isn't trivially bypassed by reading the JS.
 * It does NOT issue a session/token, so approve/reject/face-check below stay unauthenticated after this
 * check passes. Fine for a small trusted club for the MVP deadline, but flag before wider rollout:
 * add a short-lived token from this endpoint and require it on the admin-only endpoints.
 */
@RestController
public class AdminAuthController {

    private final AdminProperties adminProperties;

    public AdminAuthController(AdminProperties adminProperties) {
        this.adminProperties = adminProperties;
    }

    @PostMapping("/api/admin/auth")
    public ResponseEntity<Void> verifyPassword(@Valid @RequestBody AdminPasswordRequest request) {
        if (adminProperties.password().equals(request.password())) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
