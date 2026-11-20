package com.returnright.service;

import com.returnright.model.dto.response.ReturnResponse;
import com.returnright.model.dto.response.ReturnSummaryResponse;
import com.returnright.model.enums.ReturnStatus;
import java.util.List;
import java.util.UUID;

/** Read-only views of returns for staff. */
public interface ReturnQueryService {

  /**
   * @param status optional status filter; without it, the 200 most recent returns
   * @param flaggedOnly only returns of flagged customers
   */
  List<ReturnSummaryResponse> list(ReturnStatus status, boolean flaggedOnly);

  ReturnResponse get(UUID returnId);
}
