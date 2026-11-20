package com.returnright.gateway;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Live, thread-safe fault configuration ("break the gateway", Part 2 §12.2). Decisions use a seeded RNG so a test
 * run is reproducible. Defaults come from env vars; the admin API changes them at runtime.
 */
@Component
public class Faults {

    public record Config(int latencyMsMin, int latencyMsMax, double timeoutRate, int timeoutSleepMs, double errorRate,
                         double errorAfterCommitRate, double dropResponseRate, double declineRate,
                         double processingRate, int settleSeconds) {

        Config withOverrides(Map<String, Object> m) {
            return new Config(i(m, "latency_ms_min", latencyMsMin), i(m, "latency_ms_max", latencyMsMax),
                    d(m, "timeout_rate", timeoutRate), i(m, "timeout_sleep_ms", timeoutSleepMs),
                    d(m, "error_rate", errorRate), d(m, "error_after_commit_rate", errorAfterCommitRate),
                    d(m, "drop_response_rate", dropResponseRate), d(m, "decline_rate", declineRate),
                    d(m, "processing_rate", processingRate), i(m, "settle_seconds", settleSeconds));
        }

        private static int i(Map<String, Object> m, String k, int dflt) {
            Object v = m.get(k);
            return v instanceof Number n ? Math.max(0, n.intValue()) : dflt;
        }

        private static double d(Map<String, Object> m, String k, double dflt) {
            Object v = m.get(k);
            return v instanceof Number n ? Math.min(1.0, Math.max(0.0, n.doubleValue())) : dflt;
        }
    }

    private final Config defaults;
    private volatile Config current;
    private final Random random;
    private volatile String profile = "env-defaults";

    public Faults(@Value("${gateway.faults.latency-ms-min}") int latMin, @Value("${gateway.faults.latency-ms-max}") int latMax,
                  @Value("${gateway.faults.timeout-rate}") double timeoutRate,
                  @Value("${gateway.faults.timeout-sleep-ms}") int timeoutSleep,
                  @Value("${gateway.faults.error-rate}") double errorRate,
                  @Value("${gateway.faults.error-after-commit-rate}") double errorAfter,
                  @Value("${gateway.faults.drop-response-rate}") double drop,
                  @Value("${gateway.faults.decline-rate}") double decline,
                  @Value("${gateway.faults.processing-rate}") double processing,
                  @Value("${gateway.faults.settle-seconds}") int settle,
                  @Value("${gateway.faults.seed}") long seed) {
        this.defaults = new Config(latMin, latMax, timeoutRate, timeoutSleep, errorRate, errorAfter, drop, decline,
                processing, settle);
        this.current = defaults;
        this.random = new Random(seed);
    }

    public Config get() {
        return current;
    }

    public String profile() {
        return profile;
    }

    public void update(Map<String, Object> overrides) {
        current = current.withOverrides(overrides);
        profile = "custom";
    }

    public void reset() {
        current = defaults;
        profile = "env-defaults";
    }

    public boolean applyProfile(String name) {
        Config b = defaults;
        Config c = switch (name) {
            case "healthy" -> new Config(50, 200, 0, b.timeoutSleepMs(), 0, 0, 0, 0, 0, b.settleSeconds());
            case "slow" -> new Config(2000, 6000, 0, b.timeoutSleepMs(), 0, 0, 0, 0, 0, b.settleSeconds());
            case "flaky" -> new Config(50, 400, 0.1, b.timeoutSleepMs(), 0.2, 0, 0, 0, 0, b.settleSeconds());
            case "lost-responses" -> new Config(50, 200, 0, b.timeoutSleepMs(), 0, 0, 0.5, 0, 0, b.settleSeconds());
            case "blackhole" -> new Config(50, 200, 1.0, b.timeoutSleepMs(), 0, 0, 0, 0, 0, b.settleSeconds());
            case "declines" -> new Config(50, 200, 0, b.timeoutSleepMs(), 0, 0, 0, 0.3, 0, b.settleSeconds());
            case "processing" -> new Config(50, 200, 0, b.timeoutSleepMs(), 0, 0, 0, 0, 0.5, b.settleSeconds());
            default -> null;
        };
        if (c == null) {
            return false;
        }
        current = c;
        profile = name;
        return true;
    }

    public synchronized boolean roll(double rate) {
        return rate > 0 && random.nextDouble() < rate;
    }

    public synchronized int latencyMs() {
        Config c = current;
        int span = Math.max(0, c.latencyMsMax() - c.latencyMsMin());
        return c.latencyMsMin() + (span == 0 ? 0 : random.nextInt(span + 1));
    }

    public Map<String, Object> describe() {
        Config c = current;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("profile", profile);
        m.put("latency_ms_min", c.latencyMsMin());
        m.put("latency_ms_max", c.latencyMsMax());
        m.put("timeout_rate", c.timeoutRate());
        m.put("timeout_sleep_ms", c.timeoutSleepMs());
        m.put("error_rate", c.errorRate());
        m.put("error_after_commit_rate", c.errorAfterCommitRate());
        m.put("drop_response_rate", c.dropResponseRate());
        m.put("decline_rate", c.declineRate());
        m.put("processing_rate", c.processingRate());
        m.put("settle_seconds", c.settleSeconds());
        return m;
    }
}
