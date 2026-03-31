package com.smartinfra.smart_public_infra.service;

import com.smartinfra.smart_public_infra.exception.UnauthorizedException;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthService {

    private static final String ADMIN_SESSION_KEY = "admin";

    public boolean login(String username, String password, HttpSession session) {
        if ("admin".equals(username) && "1234".equals(password)) {
            session.setAttribute(ADMIN_SESSION_KEY, true);
            return true;
        }
        return false;
    }

    public void logout(HttpSession session) {
        session.removeAttribute(ADMIN_SESSION_KEY);
    }

    public boolean isAuthenticated(HttpSession session) {
        Object value = session.getAttribute(ADMIN_SESSION_KEY);
        return Boolean.TRUE.equals(value);
    }

    public void requireAdmin(HttpSession session) {
        if (!isAuthenticated(session)) {
            throw new UnauthorizedException("Unauthorized.");
        }
    }
}
