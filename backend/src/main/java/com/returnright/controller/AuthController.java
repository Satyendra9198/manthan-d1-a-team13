package com.returnright.controller;

import com.returnright.model.domain.StaffActor;
import com.returnright.model.dto.request.LoginRequest;
import com.returnright.model.dto.response.LoginResponse;
import com.returnright.model.dto.response.StaffProfileResponse;
import com.returnright.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Figma "Staff – Login": staff sign in with their work email. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  /**
   * @return 200 with a bearer token, 400 if a field is invalid, 401 for wrong credentials
   */
  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }

  /**
   * @return 200 with the signed-in staff member, 401 without a valid token
   */
  @GetMapping("/me")
  public StaffProfileResponse me(@AuthenticationPrincipal Jwt token) {
    return authService.currentStaff(StaffActor.from(token).id());
  }
}
