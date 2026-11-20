package com.returnright.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on background jobs (refund reconciliation). Set {@code returnright.scheduling.enabled} to
 * {@code false} to switch them off, e.g. when running several instances without a shared lock.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    prefix = "returnright.scheduling",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SchedulingConfig {}
