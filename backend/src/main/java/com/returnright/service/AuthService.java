package com.returnright.service;

import com.returnright.model.dto.request.LoginRequest;
import com.returnright.model.dto.response.LoginResponse;
import com.returnright.model.dto.response.StaffProfileResponse;
import java.util.UUID;

/** Staff sign-in. */
public interface AuthService {

  /**
   * Checks the credentials and issues a signed token carrying the staff member's role.
   *
   * @throws com.returnright.exception.InvalidCredentialsException for any failed sign-in
   */
  LoginResponse login(LoginRequest request);

  /**
   * The profile of a signed-in staff member.
   *
   * @throws com.returnright.exception.InvalidCredentialsException if the account no longer exists
   *     or was deactivated
   */
  StaffProfileResponse currentStaff(UUID staffId);
}
