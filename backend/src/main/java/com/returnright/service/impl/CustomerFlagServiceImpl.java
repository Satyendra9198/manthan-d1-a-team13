package com.returnright.service.impl;

import com.returnright.service.CustomerFlagService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * More than {@value #MAX_REQUESTS_IN_WINDOW} requests in the rolling 30 × 24 h up to now flags the
 * customer (assumption A-10). A request exactly 30 days old still counts.
 */
@Service
public class CustomerFlagServiceImpl implements CustomerFlagService {

  static final int MAX_REQUESTS_IN_WINDOW = 3;
  static final Duration WINDOW = Duration.ofDays(30);

  private final Clock clock;

  public CustomerFlagServiceImpl(Clock clock) {
    this.clock = clock;
  }

  @Override
  public boolean isFlagged(List<Instant> requestTimes) {
    Instant windowStart = clock.instant().minus(WINDOW);
    long requestsInWindow =
        requestTimes.stream().filter(time -> !time.isBefore(windowStart)).count();
    return requestsInWindow > MAX_REQUESTS_IN_WINDOW;
  }
}
