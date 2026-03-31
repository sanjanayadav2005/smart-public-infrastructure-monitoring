package com.smartinfra.smart_public_infra.controller;

import com.smartinfra.smart_public_infra.dto.AdminLoginRequest;
import com.smartinfra.smart_public_infra.dto.AdminSessionResponse;
import com.smartinfra.smart_public_infra.dto.MessageResponse;
import com.smartinfra.smart_public_infra.service.AdminAuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminAuthService adminAuthService;

    public AdminController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<MessageResponse> login(
            @RequestBody(required = false) AdminLoginRequest request,
            HttpSession session) {
        String username = request != null && request.getUsername() != null ? request.getUsername().trim() : "";
        String password = request != null && request.getPassword() != null ? request.getPassword().trim() : "";

        if (adminAuthService.login(username, password, session)) {
            return ResponseEntity.ok(new MessageResponse("Login successful."));
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new MessageResponse("Invalid username or password."));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(HttpSession session) {
        adminAuthService.logout(session);
        return ResponseEntity.ok(new MessageResponse("Logged out."));
    }

    @GetMapping("/session")
    public ResponseEntity<AdminSessionResponse> session(HttpSession session) {
        return ResponseEntity.ok(new AdminSessionResponse(adminAuthService.isAuthenticated(session)));
    }
}
