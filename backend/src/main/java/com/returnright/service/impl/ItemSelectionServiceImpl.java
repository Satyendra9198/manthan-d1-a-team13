package com.returnright.service.impl;

import com.returnright.exception.ItemSelectionRejectedException;
import com.returnright.model.domain.EligibilityResult;
import com.returnright.model.domain.ItemEligibilityInput;
import com.returnright.model.dto.request.ItemSelectionRequest;
import com.returnright.model.dto.request.SelectedItemRequest;
import com.returnright.model.dto.response.ItemSelectionResponse;
import com.returnright.model.dto.response.SelectedItemResponse;
import com.returnright.model.entity.CustomerOrder;
import com.returnright.model.entity.OrderItem;
import com.returnright.service.EligibilityService;
import com.returnright.service.ItemSelectionService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validates every selected line with the same eligibility rules as the order lookup, now using the
 * quantity the customer actually picked. All problems are reported together.
 */
@Service
public class ItemSelectionServiceImpl implements ItemSelectionService {

  private final CustomerOrderReader customerOrderReader;
  private final EligibilityService eligibilityService;

  public ItemSelectionServiceImpl(
      CustomerOrderReader customerOrderReader, EligibilityService eligibilityService) {
    this.customerOrderReader = customerOrderReader;
    this.eligibilityService = eligibilityService;
  }

  @Override
  @Transactional(readOnly = true)
  public ItemSelectionResponse validateSelection(ItemSelectionRequest request) {
    CustomerOrder order =
        customerOrderReader.findOwnedOrder(request.orderNumber(), request.email());
    Map<UUID, OrderItem> orderItemsById =
        order.getItems().stream().collect(Collectors.toMap(OrderItem::getId, Function.identity()));

    List<String> problems = new ArrayList<>();
    List<SelectedItemResponse> acceptedLines = new ArrayList<>();
    Set<UUID> seenItemIds = new HashSet<>();

    for (SelectedItemRequest selected : request.items()) {
      OrderItem item = orderItemsById.get(selected.itemId());
      if (item == null) {
        problems.add(selected.itemId() + ": This item is not part of the order.");
      } else if (!seenItemIds.add(selected.itemId())) {
        problems.add(selected.itemId() + ": This item was selected more than once.");
      } else {
        EligibilityResult eligibility = evaluate(item, selected.quantity());
        if (eligibility.eligible()) {
          acceptedLines.add(toSelectedItem(item, selected.quantity(), eligibility));
        } else {
          problems.add(selected.itemId() + ": " + eligibility.message());
        }
      }
    }

    if (!problems.isEmpty()) {
      throw new ItemSelectionRejectedException(problems);
    }

    int selectedUnitCount = acceptedLines.stream().mapToInt(SelectedItemResponse::quantity).sum();
    long estimatedRefundPaise =
        acceptedLines.stream().mapToLong(SelectedItemResponse::lineTotalPaise).sum();

    return new ItemSelectionResponse(
        order.getId(),
        order.getOrderNumber(),
        acceptedLines.size(),
        selectedUnitCount,
        estimatedRefundPaise,
        acceptedLines);
  }

  private EligibilityResult evaluate(OrderItem item, int quantityRequested) {
    return eligibilityService.evaluate(
        new ItemEligibilityInput(
            item.getCategory(),
            item.getDeliveredAt(),
            item.getQuantity(),
            customerOrderReader.quantityAlreadyReturned(item),
            quantityRequested));
  }

  private static SelectedItemResponse toSelectedItem(
      OrderItem item, int quantity, EligibilityResult eligibility) {
    return new SelectedItemResponse(
        item.getId(),
        item.getProductName(),
        item.getVariant(),
        item.getCategory(),
        item.getUnitPricePaise(),
        quantity,
        item.getUnitPricePaise() * quantity,
        eligibility.lastReturnDate());
  }
}
