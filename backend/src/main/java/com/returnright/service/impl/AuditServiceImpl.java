package com.returnright.service.impl;

import com.returnright.model.domain.Actor;
import com.returnright.model.entity.AuditLogEntry;
import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.repository.AuditLogRepository;
import com.returnright.service.AuditService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditServiceImpl implements AuditService {

  private final AuditLogRepository auditLogRepository;
  private final Clock clock;

  public AuditServiceImpl(AuditLogRepository auditLogRepository, Clock clock) {
    this.auditLogRepository = auditLogRepository;
    this.clock = clock;
  }

  /** MANDATORY: an audit entry is only ever written together with the state change it records. */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      ReturnRequest returnRequest,
      String event,
      ReturnStatus fromStatus,
      ReturnStatus toStatus,
      Actor actor,
      String details) {
    auditLogRepository.save(
        new AuditLogEntry(
            returnRequest.getId(),
            actor.role(),
            actor.id(),
            event,
            fromStatus,
            toStatus,
            details,
            clock.instant()));
  }

  @Override
  @Transactional(readOnly = true)
  public List<AuditLogEntry> history(UUID returnId) {
    return auditLogRepository.findByReturnIdOrderByIdAsc(returnId);
  }
}
