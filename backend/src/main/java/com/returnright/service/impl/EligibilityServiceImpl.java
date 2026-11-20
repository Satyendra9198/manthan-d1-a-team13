package com.returnright.service.impl;

import com.returnright.model.domain.EligibilityResult;
import com.returnright.model.domain.ItemEligibilityInput;
import com.returnright.service.EligibilityService;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.stereotype.Service;

/** Pure, deterministic eligibility rules. "Now" comes from the injected clock. */
@Service
public class EligibilityServiceImpl implements EligibilityService {

  public static final ZoneId STORE_ZONE = ZoneId.of("Asia/Kolkata");

  private final Clock clock;

  public EligibilityServiceImpl(Clock clock) {
    this.clock = clock;
  }

  @Override
  public EligibilityResult evaluate(ItemEligibilityInput input) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
