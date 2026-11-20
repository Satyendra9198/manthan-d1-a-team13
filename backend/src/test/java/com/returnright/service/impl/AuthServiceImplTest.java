package com.returnright.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.returnright.config.SecurityConfig;
import com.returnright.config.SecurityProperties;
import com.returnright.exception.InvalidCredentialsException;
import com.returnright.model.dto.request.LoginRequest;
import com.returnright.model.dto.response.LoginResponse;
import com.returnright.model.entity.StaffUser;
import com.returnright.model.enums.Role;
import com.returnright.repository.StaffUserRepository;
import com.returnright.support.WithApiSecurity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class AuthServiceImplTest {

  /** Tokens must be valid "now" for the decoder, so the clock is the real one. */
  private static final Clock CLOCK = Clock.systemUTC();

  private static final String EMAIL = "riya.warehouse@returnright.example";
  private static final String PASSWORD = "Password@123";

  private final SecurityProperties properties =
      new SecurityProperties(
          WithApiSecurity.TEST_SIGNING_KEY, Duration.ofHours(8), List.of("http://localhost:5173"));
  private final SecurityConfig securityConfig = new SecurityConfig();
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
  private final StaffUserRepository staffUserRepository = mock(StaffUserRepository.class);
  private final JwtDecoder jwtDecoder = securityConfig.jwtDecoder(properties);

  private AuthServiceImpl service;
  private StaffUser agent;

  @BeforeEach
  void setUp() {
    service =
        new AuthServiceImpl(
            staffUserRepository,
            passwordEncoder,
            securityConfig.jwtEncoder(properties),
            properties,
            CLOCK);
    agent = staff(true);
    when(staffUserRepository.findByEmail(anyString())).thenReturn(Optional.empty());
    when(staffUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(agent));
    when(staffUserRepository.findById(agent.getId())).thenReturn(Optional.of(agent));
  }

  @Test
  void correctPasswordIssuesASignedTokenCarryingTheRole() {
    LoginResponse response = service.login(new LoginRequest(EMAIL, PASSWORD));

    Jwt token = jwtDecoder.decode(response.accessToken());
    assertThat(token.getSubject()).isEqualTo(agent.getId().toString());
    assertThat(token.getClaimAsString(SecurityConfig.ROLE_CLAIM)).isEqualTo("WAREHOUSE_AGENT");
    assertThat(response.tokenType()).isEqualTo("Bearer");
    assertThat(response.staff().fullName()).isEqualTo("Riya Kapoor");
  }

  @Test
  void tokenExpiresAfterTheConfiguredLifetime() {
    Instant before = CLOCK.instant();

    LoginResponse response = service.login(new LoginRequest(EMAIL, PASSWORD));

    assertThat(response.expiresAt())
        .isBetween(before.plus(Duration.ofHours(8)), CLOCK.instant().plus(Duration.ofHours(8)));
    assertThat(jwtDecoder.decode(response.accessToken()).getExpiresAt())
        .isEqualTo(response.expiresAt().truncatedTo(ChronoUnit.SECONDS));
  }

  @Test
  void emailIsMatchedIgnoringCaseAndSpaces() {
    LoginResponse response =
        service.login(new LoginRequest("  Riya.Warehouse@ReturnRight.example ", PASSWORD));

    assertThat(response.staff().id()).isEqualTo(agent.getId());
  }

  @Test
  void wrongPasswordIsRefused() {
    assertThatThrownBy(() -> service.login(new LoginRequest(EMAIL, "wrong-password")))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Email or password is incorrect.");
  }

  @Test
  void unknownEmailIsRefusedWithTheSameError() {
    assertThatThrownBy(
            () -> service.login(new LoginRequest("nobody@returnright.example", PASSWORD)))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Email or password is incorrect.");
  }

  @Test
  void deactivatedAccountIsRefusedEvenWithTheRightPassword() {
    when(staffUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(staff(false)));

    assertThatThrownBy(() -> service.login(new LoginRequest(EMAIL, PASSWORD)))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void currentStaffReturnsTheProfile() {
    assertThat(service.currentStaff(agent.getId()).role()).isEqualTo(Role.WAREHOUSE_AGENT);
  }

  @Test
  void currentStaffForADeactivatedAccountIsRefused() {
    StaffUser deactivated = staff(false);
    when(staffUserRepository.findById(deactivated.getId())).thenReturn(Optional.of(deactivated));

    assertThatThrownBy(() -> service.currentStaff(deactivated.getId()))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  private StaffUser staff(boolean active) {
    return new StaffUser(
        UUID.randomUUID(),
        EMAIL,
        "Riya Kapoor",
        Role.WAREHOUSE_AGENT,
        passwordEncoder.encode(PASSWORD),
        active);
  }
}
