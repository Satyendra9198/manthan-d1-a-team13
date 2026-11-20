package com.returnright.web;

import com.returnright.auth.AuthService;
import com.returnright.web.Dtos.CustomerLookupRequest;
import com.returnright.web.Dtos.StaffLoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/customer/lookup")
    public AuthService.Session lookup(@Valid @RequestBody CustomerLookupRequest req, HttpServletRequest http) {
        return auth.customerLookup(req.orderNumber(), req.email(), http.getRemoteAddr());
    }

    @PostMapping("/staff/login")
    public AuthService.Session login(@Valid @RequestBody StaffLoginRequest req, HttpServletRequest http) {
        return auth.staffLogin(req.username(), req.password(), http.getRemoteAddr());
    }

    @GetMapping("/me")
    public AuthService.Principal me(@AuthenticationPrincipal Jwt jwt) {
        return AuthService.principalFrom(jwt);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        auth.logout(jwt);
        return ResponseEntity.noContent().build();
    }
}
