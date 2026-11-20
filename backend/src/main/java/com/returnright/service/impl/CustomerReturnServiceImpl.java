package com.returnright.service.impl;

import com.returnright.exception.BusinessRuleException;
import com.returnright.exception.ResourceNotFoundException;
import com.returnright.mapper.ReturnMapper;
import com.returnright.model.domain.Actor;
import com.returnright.model.domain.EligibilityResult;
import com.returnright.model.domain.ItemEligibilityInput;
import com.returnright.model.dto.request.CreateReturnRequest;
import com.returnright.model.dto.request.OrderLookupRequest;
import com.returnright.model.dto.request.ReturnItemRequest;
import com.returnright.model.dto.response.EligibilityResponse;
import com.returnright.model.dto.response.IneligibleItemResponse;
import com.returnright.model.dto.response.LabelResponse;
import com.returnright.model.dto.response.OrderItemResponse;
import com.returnright.model.dto.response.OrderResponse;
import com.returnright.model.dto.response.ReturnResponse;
import com.returnright.model.entity.CustomerOrder;
import com.returnright.model.entity.OrderItem;
import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.enums.ReturnEvent;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.repository.CustomerOrderRepository;
import com.returnright.repository.RefundRepository;
import com.returnright.repository.ReturnRequestRepository;
import com.returnright.service.AuditService;
import com.returnright.service.CustomerFlagService;
import com.returnright.service.CustomerReturnService;
import com.returnright.service.EligibilityService;
import com.returnright.service.ReturnTransitionService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerReturnServiceImpl implements CustomerReturnService {

  private static final Duration FLAG_LOOKBACK = Duration.ofDays(30);

  private final CustomerOrderRepository orderRepository;
  private final ReturnRequestRepository returnRequestRepository;
  private final RefundRepository refundRepository;
  private final EligibilityService eligibilityService;
  private final CustomerFlagService flagService;
  private final ReturnTransitionService transitionService;
  private final AuditService auditService;
  private final ReferenceCodeGenerator codeGenerator;
  private final ReturnMapper mapper;
  private final Clock clock;

  public CustomerReturnServiceImpl(
      CustomerOrderRepository orderRepository,
      ReturnRequestRepository returnRequestRepository,
      RefundRepository refundRepository,
      EligibilityService eligibilityService,
      CustomerFlagService flagService,
      ReturnTransitionService transitionService,
      AuditService auditService,
      ReferenceCodeGenerator codeGenerator,
      ReturnMapper mapper,
      Clock clock) {
    this.orderRepository = orderRepository;
    this.returnRequestRepository = returnRequestRepository;
    this.refundRepository = refundRepository;
    this.eligibilityService = eligibilityService;
    this.flagService = flagService;
    this.transitionService = transitionService;
    this.auditService = auditService;
    this.codeGenerator = codeGenerator;
    this.mapper = mapper;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public OrderResponse lookupOrder(OrderLookupRequest request) {
    CustomerOrder order = findVerifiedOrder(request.orderNumber(), request.email());
    List<OrderItemResponse> items =
        order.getItems().stream().map(item -> toOrderItem(order, item)).toList();
    return new OrderResponse(
        order.getOrderNumber(),
        order.getCustomer().getFullName(),
        order.getOrderedAt(),
        order.getDeliveredAt(),
        items);
  }

  @Override
  @Transactional
  public ReturnResponse createReturn(CreateReturnRequest request) {
    CustomerOrder order = findVerifiedOrder(request.orderNumber(), request.email());
    rejectDuplicateItems(request.items());

    List<ReturnItemRequest> eligible = new ArrayList<>();
    List<IneligibleItemResponse> ineligible = new ArrayList<>();
    for (ReturnItemRequest itemRequest : request.items()) {
      OrderItem orderItem =
          order
              .findItem(itemRequest.orderItemId())
              .orElseThrow(
                  () ->
                      new BusinessRuleException(
                          "UNKNOWN_ORDER_ITEM",
                          "Item " + itemRequest.orderItemId() + " is not part of this order."));
      EligibilityResult result = evaluate(order, orderItem, itemRequest.quantity());
      if (result.eligible()) {
        eligible.add(itemRequest);
      } else {
        ineligible.add(
            new IneligibleItemResponse(orderItem.getId(), result.reason(), result.message()));
      }
    }
    if (eligible.isEmpty()) {
      throw new BusinessRuleException(
          "NO_ELIGIBLE_ITEMS",
          "None of the selected items can be returned.",
          ineligible.stream().map(item -> item.orderItemId() + ": " + item.message()).toList());
    }

    Instant now = clock.instant();
    boolean flagged = isFlagged(order, now);
    ReturnRequest returnRequest =
        new ReturnRequest(
            codeGenerator.returnNumber(), order, request.refundMethod(), flagged, now);
    for (ReturnItemRequest itemRequest : eligible) {
      returnRequest.addItem(
          order.findItem(itemRequest.orderItemId()).orElseThrow(),
          itemRequest.quantity(),
          itemRequest.reason(),
          itemRequest.note());
    }
    returnRequestRepository.saveAndFlush(returnRequest);

    Actor customer = Actor.customer(order.getCustomer().getEmail());
    transitionService.recordCreation(
        returnRequest,
        customer,
        flagged ? "Customer flagged for review: more than 3 returns in 30 days" : null);
    if (!flagged) {
      transitionService.apply(
          returnRequest, ReturnEvent.APPROVE, Actor.system(), "Auto-approved: items eligible");
    }
    return mapper.toResponse(
        returnRequest, null, auditService.history(returnRequest.getId()), ineligible);
  }

  @Override
  @Transactional(readOnly = true)
  public ReturnResponse getReturn(UUID returnId, String orderNumber, String email) {
    return toResponse(findVerifiedReturn(returnId, orderNumber, email));
  }

  @Override
  @Transactional
  public ReturnResponse cancelReturn(UUID returnId, String orderNumber, String email) {
    ReturnRequest returnRequest = findVerifiedReturn(returnId, orderNumber, email);
    transitionService.apply(
        returnRequest, ReturnEvent.CANCEL, Actor.customer(email), "Cancelled by customer");
    return toResponse(returnRequest);
  }

  @Override
  @Transactional
  public ReturnResponse markShipped(UUID returnId, String orderNumber, String email) {
    ReturnRequest returnRequest = findVerifiedReturn(returnId, orderNumber, email);
    transitionService.apply(
        returnRequest, ReturnEvent.MARK_IN_TRANSIT, Actor.customer(email), "Parcel handed over");
    return toResponse(returnRequest);
  }

  @Override
  @Transactional(readOnly = true)
  public LabelResponse getLabel(UUID returnId, String orderNumber, String email) {
    LabelResponse label = mapper.toLabel(findVerifiedReturn(returnId, orderNumber, email));
    if (label == null) {
      throw new BusinessRuleException(
          "LABEL_NOT_AVAILABLE", "A return label is issued once the return is approved.");
    }
    return label;
  }

  private OrderItemResponse toOrderItem(CustomerOrder order, OrderItem item) {
    long alreadyReturned = quantityOnReturns(item);
    EligibilityResult result = evaluate(order, item, 1);
    int remaining = (int) Math.max(0, item.getQuantity() - alreadyReturned);
    return new OrderItemResponse(
        item.getId(),
        item.getProduct().getName(),
        item.getProduct().getCategory(),
        item.getQuantity(),
        item.getUnitPricePaise(),
        result.eligible() ? remaining : 0,
        new EligibilityResponse(
            result.eligible(), result.reason(), result.message(), result.lastReturnDate()));
  }

  private EligibilityResult evaluate(CustomerOrder order, OrderItem item, int quantityRequested) {
    return eligibilityService.evaluate(
        new ItemEligibilityInput(
            item.getProduct().getCategory(),
            order.getDeliveredAt(),
            item.getQuantity(),
            (int) quantityOnReturns(item),
            quantityRequested));
  }

  private long quantityOnReturns(OrderItem item) {
    return returnRequestRepository.sumQuantityOnReturns(item.getId(), ReturnStatus.CANCELLED);
  }

  private boolean isFlagged(CustomerOrder order, Instant now) {
    List<Instant> requestTimes =
        new ArrayList<>(
            returnRequestRepository.findRequestTimes(
                order.getCustomer().getId(), ReturnStatus.CANCELLED, now.minus(FLAG_LOOKBACK)));
    requestTimes.add(now);
    return flagService.isFlagged(requestTimes);
  }

  private static void rejectDuplicateItems(List<ReturnItemRequest> items) {
    Set<UUID> seen = new HashSet<>();
    for (ReturnItemRequest item : items) {
      if (!seen.add(item.orderItemId())) {
        throw new IllegalArgumentException(
            "Order item " + item.orderItemId() + " appears more than once");
      }
    }
  }

  private CustomerOrder findVerifiedOrder(String orderNumber, String email) {
    return orderRepository
        .findByOrderNumber(orderNumber.trim())
        .filter(order -> order.getCustomer().getEmail().equalsIgnoreCase(email.trim()))
        .orElseThrow(
            () -> new ResourceNotFoundException("No order matches that order number and email."));
  }

  private ReturnRequest findVerifiedReturn(UUID returnId, String orderNumber, String email) {
    return returnRequestRepository
        .findDetailedById(returnId)
        .filter(r -> r.getOrder().getOrderNumber().equals(orderNumber.trim()))
        .filter(r -> r.getCustomer().getEmail().equalsIgnoreCase(email.trim()))
        .orElseThrow(() -> new ResourceNotFoundException("Return not found."));
  }

  private ReturnResponse toResponse(ReturnRequest returnRequest) {
    return mapper.toResponse(
        returnRequest,
        refundRepository.findByReturnId(returnRequest.getId()).orElse(null),
        auditService.history(returnRequest.getId()),
        List.of());
  }
}
