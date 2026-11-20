package com.returnright.service.impl;

import com.returnright.service.CustomerFlagService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CustomerFlagServiceImpl implements CustomerFlagService {

  private final Clock clock;

  public CustomerFlagServiceImpl(Clock clock) {
    this.clock = clock;
  }

  @Override
  public boolean isFlagged(List<Instant> requestTimes) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
