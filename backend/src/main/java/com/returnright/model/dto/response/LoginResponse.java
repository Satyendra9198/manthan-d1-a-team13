package com.returnright.model.dto.response;

import java.time.Instant;

/**
 * A successful sign-in. Send the token as {@code Authorization: Bearer <accessToken>}.
 *
 * @param expiresAt after this the staff member must sign in again
 */
public record LoginResponse(
    String accessToken, String tokenType, Instant expiresAt, StaffProfileResponse staff) {}
