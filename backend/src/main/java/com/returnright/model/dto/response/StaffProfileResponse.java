package com.returnright.model.dto.response;

import com.returnright.model.enums.Role;
import java.util.UUID;

/** The signed-in staff member. */
public record StaffProfileResponse(UUID id, String email, String fullName, Role role) {}
