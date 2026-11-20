package com.returnright.service.impl;

import com.returnright.model.domain.PhotoUpload;
import com.returnright.model.domain.ValidatedPhoto;
import com.returnright.model.dto.request.ItemSelectionRequest;
import com.returnright.model.dto.request.ReturnItemRequest;
import com.returnright.model.dto.request.SelectedItemRequest;
import com.returnright.model.dto.request.SubmitReturnRequest;
import com.returnright.model.dto.response.ItemSelectionResponse;
import com.returnright.model.dto.response.ReturnCreatedResponse;
import com.returnright.model.dto.response.ReturnLineResponse;
import com.returnright.model.dto.response.SelectedItemResponse;
import com.returnright.model.entity.CustomerOrder;
import com.returnright.model.entity.OrderItem;
import com.returnright.model.entity.ReturnItem;
import com.returnright.model.entity.ReturnPhoto;
import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.enums.ReturnEvent;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.model.enums.Role;
import com.returnright.repository.ReturnRequestRepository;
import com.returnright.service.CustomerFlagService;
import com.returnright.service.ItemSelectionService;
import com.returnright.service.PhotoStorage;
import com.returnright.service.ReturnStateMachineService;
import com.returnright.service.ReturnSubmissionService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the return in one transaction while holding a lock on the order, so two submissions for
 * the same order cannot both claim the same units.
 *
 * <p>Order of work: lock the order, re-check the items (same rules as the selection step), check
 * the photos, decide on flagging, save the return, then store the photos. If anything fails after a
 * photo is stored, the stored photos are deleted before the error is rethrown.
 */
@Service
public class ReturnSubmissionServiceImpl implements ReturnSubmissionService {

  private static final Duration FLAG_LOOKBACK = Duration.ofDays(30);

  private final CustomerOrderReader customerOrderReader;
  private final ItemSelectionService itemSelectionService;
  private final ReturnPhotoPolicy returnPhotoPolicy;
  private final CustomerFlagService customerFlagService;
  private final ReturnStateMachineService returnStateMachineService;
  private final ReturnRequestRepository returnRequestRepository;
  private final PhotoStorage photoStorage;
  private final Clock clock;

  public ReturnSubmissionServiceImpl(
      CustomerOrderReader customerOrderReader,
      ItemSelectionService itemSelectionService,
      ReturnPhotoPolicy returnPhotoPolicy,
      CustomerFlagService customerFlagService,
      ReturnStateMachineService returnStateMachineService,
      ReturnRequestRepository returnRequestRepository,
      PhotoStorage photoStorage,
      Clock clock) {
    this.customerOrderReader = customerOrderReader;
    this.itemSelectionService = itemSelectionService;
    this.returnPhotoPolicy = returnPhotoPolicy;
    this.customerFlagService = customerFlagService;
    this.returnStateMachineService = returnStateMachineService;
    this.returnRequestRepository = returnRequestRepository;
    this.photoStorage = photoStorage;
    this.clock = clock;
  }

  @Override
  @Transactional
  public ReturnCreatedResponse submit(SubmitReturnRequest request, List<PhotoUpload> photos) {
    CustomerOrder order =
        customerOrderReader.findOwnedOrderForUpdate(request.orderNumber(), request.email());
    ItemSelectionResponse selection =
        itemSelectionService.validateSelection(toSelectionRequest(request));
    Map<UUID, List<ValidatedPhoto>> photosByItem =
        returnPhotoPolicy.validate(request.items(), photos);

    Instant now = clock.instant();
    boolean flagged = isFlagged(order.getCustomerEmail(), now);

    ReturnRequest returnRequest =
        new ReturnRequest(
            UUID.randomUUID(), nextRmaNumber(now), order, request.refundMethod(), flagged, now);
    if (!flagged) {
      returnRequest.moveTo(
          returnStateMachineService.transition(
              ReturnStatus.REQUESTED, ReturnEvent.APPROVE, Role.SYSTEM),
          now);
    }

    Map<UUID, OrderItem> orderItemsById =
        order.getItems().stream().collect(Collectors.toMap(OrderItem::getId, Function.identity()));
    List<String> storedKeys = new ArrayList<>();
    try {
      for (ReturnItemRequest itemRequest : request.items()) {
        ReturnItem returnItem =
            new ReturnItem(
                UUID.randomUUID(),
                orderItemsById.get(itemRequest.itemId()),
                itemRequest.quantity(),
                itemRequest.reason(),
                blankToNull(itemRequest.comment()));
        returnRequest.addItem(returnItem);
        for (ValidatedPhoto photo : photosByItem.get(itemRequest.itemId())) {
          returnItem.addPhoto(storePhoto(returnRequest.getId(), photo, now, storedKeys));
        }
      }
      returnRequestRepository.saveAndFlush(returnRequest);
    } catch (RuntimeException ex) {
      storedKeys.forEach(photoStorage::delete);
      throw ex;
    }

    return toResponse(returnRequest, selection);
  }

  private boolean isFlagged(String email, Instant now) {
    List<Instant> requestTimes =
        new ArrayList<>(
            returnRequestRepository.findRequestTimes(
                email, ReturnStatus.CANCELLED, now.minus(FLAG_LOOKBACK)));
    requestTimes.add(now);
    return customerFlagService.isFlagged(requestTimes);
  }

  /** {@code RMA-<year in Asia/Kolkata>-<5-digit sequence>}, e.g. {@code RMA-2026-00731}. */
  private String nextRmaNumber(Instant now) {
    int year = LocalDate.ofInstant(now, EligibilityServiceImpl.STORE_ZONE).getYear();
    return String.format("RMA-%d-%05d", year, returnRequestRepository.nextRmaSequence());
  }

  private ReturnPhoto storePhoto(
      UUID returnId, ValidatedPhoto photo, Instant now, List<String> storedKeys) {
    UUID photoId = UUID.randomUUID();
    String key = "returns/" + returnId + "/" + photoId + "." + photo.format().getExtension();
    photoStorage.store(key, photo.content());
    storedKeys.add(key);
    return new ReturnPhoto(
        photoId, key, photo.format().getContentType(), photo.content().length, now);
  }

  private static ItemSelectionRequest toSelectionRequest(SubmitReturnRequest request) {
    List<SelectedItemRequest> selected =
        request.items().stream()
            .map(item -> new SelectedItemRequest(item.itemId(), item.quantity()))
            .toList();
    return new ItemSelectionRequest(request.orderNumber(), request.email(), selected);
  }

  private ReturnCreatedResponse toResponse(
      ReturnRequest returnRequest, ItemSelectionResponse selection) {
    Map<UUID, SelectedItemResponse> selectedById =
        selection.items().stream()
            .collect(Collectors.toMap(SelectedItemResponse::itemId, Function.identity()));
    List<ReturnLineResponse> lines =
        returnRequest.getItems().stream()
            .map(
                item -> {
                  SelectedItemResponse selected = selectedById.get(item.getOrderItem().getId());
                  return new ReturnLineResponse(
                      item.getOrderItem().getId(),
                      selected.productName(),
                      item.getQuantity(),
                      item.getReason(),
                      selected.lineTotalPaise(),
                      item.getPhotos().size());
                })
            .toList();

    return new ReturnCreatedResponse(
        returnRequest.getId(),
        returnRequest.getRmaNumber(),
        returnStateMachineService.timelineStepFor(returnRequest.getStatus()),
        returnRequest.getStatus() == ReturnStatus.APPROVED,
        returnRequest.getRefundMethod(),
        selection.estimatedRefundPaise(),
        returnRequest.getCreatedAt(),
        lines);
  }

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text.strip();
  }
}
