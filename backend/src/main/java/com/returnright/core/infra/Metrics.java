package com.returnright.core.infra;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Business metrics (Part 2 §15.1). Micrometer name "rr.x.y" is exported as rr_x_y[_total]. Tags: enums only. */
@Component
public class Metrics {

    private final MeterRegistry registry;

    public Metrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void transition(String from, String to, String event) {
        Counter.builder("rr.return.transitions").tag("from_state", from).tag("to_state", to).tag("event", event)
                .register(registry).increment();
    }

    public void illegalTransition(String from, String event, String code) {
        Counter.builder("rr.illegal.transitions").tag("from_state", from).tag("event", event).tag("code", code)
                .register(registry).increment();
    }

    public void returnRequested() {
        registry.counter("rr.returns.requested").increment();
    }

    public void eligibility(boolean eligible, String reasonCode) {
        Counter.builder("rr.eligibility.checks").tag("result", eligible ? "eligible" : "ineligible")
                .tag("reason_code", reasonCode).register(registry).increment();
    }

    public void refundAttempt(String method, String outcome) {
        Counter.builder("rr.refund.attempts").tag("method", method).tag("outcome", outcome).register(registry)
                .increment();
    }

    public void refundAmount(String method, long paise) {
        Counter.builder("rr.refund.amount.paise").tag("method", method).register(registry).increment(paise);
    }

    public void gatewayCall(String endpoint, String outcome, Duration d) {
        Timer.builder("rr.gateway.request.duration").tag("endpoint", endpoint).tag("outcome", outcome)
                .publishPercentileHistogram()
                .serviceLevelObjectives(Duration.ofMillis(100), Duration.ofMillis(250), Duration.ofMillis(500),
                        Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3), Duration.ofSeconds(5))
                .register(registry).record(d);
    }

    public void reconcilerRun(String result) {
        Counter.builder("rr.reconciler.runs").tag("result", result).register(registry).increment();
    }

    public void idempotentReplay(String route) {
        Counter.builder("rr.idempotency.replays").tag("route", route).register(registry).increment();
    }

    public void conflict(String code) {
        Counter.builder("rr.http.conflicts").tag("code", code).register(registry).increment();
    }

    public void customerFlagged() {
        registry.counter("rr.customers.flagged.events").increment();
    }

    public void loginFailure(String kind) {
        Counter.builder("rr.login.failures").tag("kind", kind).register(registry).increment();
    }

    public MeterRegistry registry() {
        return registry;
    }
}
