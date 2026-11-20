package com.returnright.eligibility;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Pure, deterministic return-eligibility rules (assumptions A-01, A-02, A-18). "Now" comes from the
 * injected clock.
 */
public class EligibilityEngine {

  public static final ZoneId STORE_ZONE = ZoneId.of("Asia/Kolkata");

  private final Clock clock;

  public EligibilityEngine(Clock clock) {
    this.clock = clock;
  }

  public EligibilityResult evaluate(ItemEligibilityInput input) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
