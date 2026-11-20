package com.returnright.config;

import com.returnright.model.enums.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Hackathon-level auth: one seeded staff user per role, HTTP Basic, passwords from environment
 * variables. Customers don't log in; customer endpoints verify order number plus email instead.
 */
@Configuration
public class SecurityConfig {

  private static final String[] STAFF_ROLES = {
    Role.WAREHOUSE_AGENT.name(), Role.FINANCE_REVIEWER.name(), Role.OPERATIONS_MANAGER.name()
  };

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/v1/customer/**", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/api/v1/warehouse/**")
                    .hasRole(Role.WAREHOUSE_AGENT.name())
                    .requestMatchers("/api/v1/finance/**")
                    .hasRole(Role.FINANCE_REVIEWER.name())
                    .requestMatchers("/api/v1/operations/**")
                    .hasRole(Role.OPERATIONS_MANAGER.name())
                    .requestMatchers("/api/v1/returns/**")
                    .hasAnyRole(STAFF_ROLES)
                    .anyRequest()
                    .denyAll())
        .httpBasic(Customizer.withDefaults());
    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  public UserDetailsService staffUsers(
      PasswordEncoder encoder,
      @Value("${returnright.staff.warehouse-password}") String warehousePassword,
      @Value("${returnright.staff.finance-password}") String financePassword,
      @Value("${returnright.staff.operations-password}") String operationsPassword) {
    return new InMemoryUserDetailsManager(
        staffUser("warehouse", warehousePassword, Role.WAREHOUSE_AGENT, encoder),
        staffUser("finance", financePassword, Role.FINANCE_REVIEWER, encoder),
        staffUser("operations", operationsPassword, Role.OPERATIONS_MANAGER, encoder));
  }

  private static UserDetails staffUser(
      String username, String password, Role role, PasswordEncoder encoder) {
    return User.withUsername(username)
        .password(encoder.encode(password))
        .roles(role.name())
        .build();
  }
}
