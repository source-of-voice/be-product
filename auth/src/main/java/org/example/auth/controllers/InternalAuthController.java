package org.example.auth.controllers;

import org.example.auth.services.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/auth")
public class InternalAuthController {

    private final AuthService authService;

    public InternalAuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/token-version/{userId}")
    public long getTokenVersion(@PathVariable Long userId) {
        return authService.getCurrentTokenVersion(userId);
    }
}
