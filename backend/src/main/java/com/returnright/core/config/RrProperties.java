package com.returnright.core.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed application settings ({@code rr.*} in application.yml). */
@ConfigurationProperties(prefix = "rr")
public record RrProperties(
        String businessTz,
        long financeThresholdPaise,
        int flagThreshold,
        int flagWindowDays,
        int labelTtlDays,
        Jwt jwt,
        Security security,
        Gateway gateway,
        Jobs jobs,
        Kafka kafka,
        Warehouse warehouse) {

    public record Jwt(String secret, String issuer, int customerTtlMinutes, int staffTtlMinutes) {}

    public record Security(int loginMaxFailures, int loginLockoutSeconds, int lookupPerMinute, List<String> corsOrigins) {}

    public record Gateway(String url, int timeoutMs) {}

    public record Jobs(
            boolean enabled,
            long reconcilerIntervalMs,
            int stalePendingSeconds,
            long labelExpiryIntervalMs,
            long outboxIntervalMs) {}

    public record Kafka(boolean enabled, String topic) {}

    public record Warehouse(String name, String line1, String city, String pincode) {}
}
