package com.returnright.service;

import com.returnright.model.domain.Actor;
import com.returnright.model.dto.response.DashboardResponse;
import com.returnright.model.dto.response.ReturnResponse;
import java.util.UUID;

public interface OperationsService {

  /** Approves a flagged customer's return held for review. */
  ReturnResponse approve(UUID returnId, Actor actor);

  ReturnResponse reject(UUID returnId, String reason, Actor actor);

  DashboardResponse dashboard();
}
