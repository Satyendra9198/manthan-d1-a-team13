package com.returnright.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.returnright.exception.InvalidCredentialsException;
import com.returnright.model.dto.request.LoginRequest;
import com.returnright.model.dto.response.LoginResponse;
import com.returnright.model.dto.response.StaffProfileResponse;
import com.returnright.model.enums.Role;
import com.returnright.service.AuthService;
import com.returnright.support.WithApiSecurity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@WithApiSecurity
class AuthControllerTest {

  private static final UUID STAFF_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
  private static final StaffProfileResponse PROFILE =
      new StaffProfileResponse(
          STAFF_ID, "riya.warehouse@returnright.example", "Riya Kapoor", Role.WAREHOUSE_AGENT);

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthService authService;

  @Test
  void correctCredentialsReturnABearerToken() throws Exception {
    when(authService.login(any(LoginRequest.class)))
        .thenReturn(
            new LoginResponse(
                "token-value", "Bearer", Instant.parse("2026-11-20T14:30:00Z"), PROFILE));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "riya.warehouse@returnright.example", "password": "Password@123"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("token-value"))
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.staff.role").value("WAREHOUSE_AGENT"));
  }

  @Test
  void wrongCredentialsAreReportedAsUnauthorized() throws Exception {
    when(authService.login(any(LoginRequest.class))).thenThrow(new InvalidCredentialsException());

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "riya.warehouse@returnright.example", "password": "nope"}
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void blankPasswordIsRejectedAsValidationFailed() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "riya.warehouse@returnright.example", "password": ""}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void profileWithoutATokenIsRejectedAsUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void profileWithATokenReturnsTheSignedInStaffMember() throws Exception {
    when(authService.currentStaff(STAFF_ID)).thenReturn(PROFILE);

    mockMvc
        .perform(
            get("/api/v1/auth/me")
                .with(
                    jwt()
                        .jwt(
                            token ->
                                token
                                    .subject(STAFF_ID.toString())
                                    .claim("role", "WAREHOUSE_AGENT"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Riya Kapoor"));
  }
}
