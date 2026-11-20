package com.returnright.service.impl;

import com.returnright.exception.ResourceNotFoundException;
import com.returnright.mapper.ReturnMapper;
import com.returnright.model.dto.response.ReturnResponse;
import com.returnright.model.dto.response.ReturnSummaryResponse;
import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.repository.RefundRepository;
import com.returnright.repository.ReturnRequestRepository;
import com.returnright.service.AuditService;
import com.returnright.service.ReturnQueryService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReturnQueryServiceImpl implements ReturnQueryService {

  private final ReturnRequestRepository returnRequestRepository;
  private final RefundRepository refundRepository;
  private final AuditService auditService;
  private final ReturnMapper mapper;

  public ReturnQueryServiceImpl(
      ReturnRequestRepository returnRequestRepository,
      RefundRepository refundRepository,
      AuditService auditService,
      ReturnMapper mapper) {
    this.returnRequestRepository = returnRequestRepository;
    this.refundRepository = refundRepository;
    this.auditService = auditService;
    this.mapper = mapper;
  }

  @Override
  public List<ReturnSummaryResponse> list(ReturnStatus status, boolean flaggedOnly) {
    List<ReturnRequest> returns;
    if (status == null) {
      returns =
          returnRequestRepository.findTop200ByOrderByCreatedAtDesc().stream()
              .filter(returnRequest -> !flaggedOnly || returnRequest.isFlagged())
              .toList();
    } else if (flaggedOnly) {
      returns = returnRequestRepository.findByFlaggedTrueAndStatusOrderByCreatedAtAsc(status);
    } else {
      returns = returnRequestRepository.findByStatusOrderByCreatedAtAsc(status);
    }
    return returns.stream().map(mapper::toSummary).toList();
  }

  @Override
  public ReturnResponse get(UUID returnId) {
    ReturnRequest returnRequest =
        returnRequestRepository
            .findDetailedById(returnId)
            .orElseThrow(() -> new ResourceNotFoundException("Return " + returnId + " not found"));
    return mapper.toResponse(
        returnRequest,
        refundRepository.findByReturnId(returnId).orElse(null),
        auditService.history(returnId),
        List.of());
  }
}
