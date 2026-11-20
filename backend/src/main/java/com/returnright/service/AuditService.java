package com.returnright.service;

import com.returnright.model.domain.Actor;
import com.returnright.model.entity.AuditLogEntry;
import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.enums.ReturnStatus;
import java.util.List;
import java.util.UUID;

/** Append-only audit trail of return state changes. */
public interface AuditService {

  /** Must run inside the transaction that changes the return's state. */
  void record(
      ReturnRequest returnRequest,
      String event,
      ReturnStatus fromStatus,
      ReturnStatus toStatus,
      Actor actor,
      String details);

  List<AuditLogEntry> history(UUID returnId);
}
