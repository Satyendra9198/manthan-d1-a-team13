package com.returnright.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Staff authentication settings.
 *
 * @param jwtSecret HMAC key for signing staff tokens; at least 32 characters, from {@code
 *     JWT_SECRET}
 * @param tokenTtl how long a staff token stays valid
 * @param corsAllowedOrigins browser origins allowed to call the API, e.g. the Vite dev server
 */
@Validated
@ConfigurationProperties(prefix = "returnright.security")
public record SecurityProperties(
    @NotNull @Size(min = 32, message = "must be at least 32 characters; set JWT_SECRET")
        String jwtSecret,
    @NotNull Duration tokenTtl,
    @NotEmpty List<String> corsAllowedOrigins) {}
