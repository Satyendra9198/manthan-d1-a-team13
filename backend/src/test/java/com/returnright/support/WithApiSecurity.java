package com.returnright.support;

import com.returnright.config.ClockConfig;
import com.returnright.config.SecurityConfig;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/** Loads the real security rules into a {@code @WebMvcTest} slice, with a test-only signing key. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({ClockConfig.class, SecurityConfig.class})
@TestPropertySource(
    properties = {
      "returnright.security.jwt-secret=" + WithApiSecurity.TEST_SIGNING_KEY,
      "returnright.security.token-ttl=8h",
      "returnright.security.cors-allowed-origins=http://localhost:5173"
    })
public @interface WithApiSecurity {

  String TEST_SIGNING_KEY = "test-only-signing-key-0123456789abcdef";
}
