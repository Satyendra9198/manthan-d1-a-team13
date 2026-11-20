package com.returnright.service.impl;

import com.returnright.service.CustomerFlagService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** More than 3 non-cancelled requests in the rolling 30 days (inclusive) flags the customer. */
@Service
public class CustomerFlagServiceImpl implements CustomerFlagService {

  static final Duration WINDOW = Duration.ofDays(30);
  static final int MAX_REQUESTS_BEFORE_FLAG = 3;

  private final Clock clock;

  public CustomerFlagServiceImpl(Clock clock) {
    this.clock = clock;
  }

  @Override
  public boolean isFlagged(List<Instant> requestTimes) {
    Instant now = clock.instant();
    Instant windowStart = now.minus(WINDOW);
    long recentRequests =
        requestTimes.stream()
            .filter(time -> !time.isBefore(windowStart) && !time.isAfter(now))
            .count();
    return recentRequests > MAX_REQUESTS_BEFORE_FLAG;
  }
}
