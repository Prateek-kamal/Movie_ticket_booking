package com.example.seats;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class Auth {
    @Value("${app.admin-token}")
    private String adminToken;

    private String token(String header) {
        if (header == null || !header.startsWith("Bearer ") || header.length() < 8) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Missing bearer token");
        }
        return header.substring(7).trim();
    }

    public String user(String header) {
        return token(header);
    }

    public void admin(String header) {
        if (!token(header).equals(adminToken)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Admin only");
        }
    }
}
