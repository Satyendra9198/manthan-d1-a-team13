package com.returnright.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.returnright.exception.ItemSelectionRejectedException;
import com.returnright.exception.OrderNotFoundException;
import com.returnright.model.dto.request.ItemSelectionRequest;
import com.returnright.model.dto.request.OrderLookupRequest;
import com.returnright.model.dto.response.ItemSelectionResponse;
import com.returnright.model.dto.response.OrderItemResponse;
import com.returnright.model.dto.response.OrderLookupResponse;
import com.returnright.model.dto.response.SelectedItemResponse;
import com.returnright.model.enums.EligibilityReason;
import com.returnright.model.enums.ProductCategory;
import com.returnright.service.ItemSelectionService;
import com.returnright.service.OrderLookupService;
import com.returnright.support.WithApiSecurity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerOrderController.class)
@WithApiSecurity
class CustomerOrderControllerTest {

  private static final String LOOKUP_URL = "/api/v1/customer/orders/lookup";
  private static final String SELECTION_URL = "/api/v1/customer/orders/selection/validate";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private OrderLookupService orderLookupService;

  @MockitoBean private ItemSelectionService itemSelectionService;

  @Test
  void validLookupReturnsTheOrderWithItemEligibility() throws Exception {
    OrderItemResponse line =
        new OrderItemResponse(
            UUID.randomUUID(),
            "SKU-LINEN-M",
            "Linen shirt",
            "M · Sand",
            ProductCategory.APPAREL,
            189_900,
            1,
            1,
            Instant.parse("2026-11-15T04:30:00Z"),
            true,
            EligibilityReason.ELIGIBLE,
            "You can return this item until 15 Dec 2026.",
            LocalDate.of(2026, 12, 15));
    when(orderLookupService.findOrder(any(OrderLookupRequest.class)))
        .thenReturn(
            new OrderLookupResponse(
                UUID.randomUUID(),
                "RR-10482",
                Instant.parse("2026-11-10T04:30:00Z"),
                1,
                List.of(line)));

    mockMvc
        .perform(
            post(LOOKUP_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"orderNumber": "RR-10482", "email": "aditi.sharma@example.com"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderNumber").value("RR-10482"))
        .andExpect(jsonPath("$.returnableItemCount").value(1))
        .andExpect(jsonPath("$.items[0].category").value("APPAREL"))
        .andExpect(jsonPath("$.items[0].returnable").value(true))
        .andExpect(jsonPath("$.items[0].lastReturnDate").value("2026-12-15"));
  }

  @Test
  void unmatchedOrderIsReportedAsNotFound() throws Exception {
    when(orderLookupService.findOrder(any(OrderLookupRequest.class)))
        .thenThrow(new OrderNotFoundException());

    mockMvc
        .perform(
            post(LOOKUP_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"orderNumber": "RR-99999", "email": "aditi.sharma@example.com"}
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
  }

  @Test
  void blankOrderNumberAndMalformedEmailAreRejectedAsValidationFailed() throws Exception {
    mockMvc
        .perform(
            post(LOOKUP_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"orderNumber": " ", "email": "not-an-email"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.details.length()").value(2));

    verifyNoInteractions(orderLookupService);
  }

  @Test
  void validSelectionReturnsTheSummaryForTheFooter() throws Exception {
    UUID itemId = UUID.fromString("11111111-0000-0000-0000-000000000001");
    SelectedItemResponse line =
        new SelectedItemResponse(
            itemId,
            "Linen shirt",
            "M / Sand",
            ProductCategory.APPAREL,
            189_900,
            2,
            379_800,
            LocalDate.of(2026, 12, 15));
    when(itemSelectionService.validateSelection(any(ItemSelectionRequest.class)))
        .thenReturn(
            new ItemSelectionResponse(UUID.randomUUID(), "RR-10482", 1, 2, 379_800, List.of(line)));

    mockMvc
        .perform(
            post(SELECTION_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"orderNumber": "RR-10482", "email": "aditi.sharma@example.com",
                     "items": [{"itemId": "11111111-0000-0000-0000-000000000001", "quantity": 2}]}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.selectedItemCount").value(1))
        .andExpect(jsonPath("$.selectedUnitCount").value(2))
        .andExpect(jsonPath("$.estimatedRefundPaise").value(379_800))
        .andExpect(jsonPath("$.items[0].itemId").value(itemId.toString()))
        .andExpect(jsonPath("$.items[0].lineTotalPaise").value(379_800));
  }

  @Test
  void emptySelectionIsRejectedAsValidationFailed() throws Exception {
    mockMvc
        .perform(
            post(SELECTION_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"orderNumber": "RR-10482", "email": "aditi.sharma@example.com", "items": []}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.details[0]").value("items: must not be empty"));

    verifyNoInteractions(itemSelectionService);
  }

  @Test
  void zeroQuantityIsRejectedAsValidationFailed() throws Exception {
    mockMvc
        .perform(
            post(SELECTION_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"orderNumber": "RR-10482", "email": "aditi.sharma@example.com",
                     "items": [{"itemId": "11111111-0000-0000-0000-000000000001", "quantity": 0}]}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.details[0]").value("items[0].quantity: must be greater than 0"));

    verifyNoInteractions(itemSelectionService);
  }

  @Test
  void malformedItemIdIsRejectedAsMalformedRequest() throws Exception {
    mockMvc
        .perform(
            post(SELECTION_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"orderNumber": "RR-10482", "email": "aditi.sharma@example.com",
                     "items": [{"itemId": "not-a-uuid", "quantity": 1}]}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

    verifyNoInteractions(itemSelectionService);
  }

  @Test
  void nonReturnableSelectionIsReportedAsUnprocessableWithEachProblem() throws Exception {
    when(itemSelectionService.validateSelection(any(ItemSelectionRequest.class)))
        .thenThrow(
            new ItemSelectionRejectedException(
                List.of(
                    "11111111-0000-0000-0000-000000000004: "
                        + "Innerwear can't be returned for hygiene and safety reasons.")));

    mockMvc
        .perform(
            post(SELECTION_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"orderNumber": "RR-10482", "email": "aditi.sharma@example.com",
                     "items": [{"itemId": "11111111-0000-0000-0000-000000000004", "quantity": 1}]}
                    """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("SELECTION_NOT_RETURNABLE"))
        .andExpect(jsonPath("$.details.length()").value(1));
  }

  @Test
  void selectionForUnmatchedOrderIsReportedAsNotFound() throws Exception {
    when(itemSelectionService.validateSelection(any(ItemSelectionRequest.class)))
        .thenThrow(new OrderNotFoundException());

    mockMvc
        .perform(
            post(SELECTION_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"orderNumber": "RR-10482", "email": "someone@example.com",
                     "items": [{"itemId": "11111111-0000-0000-0000-000000000001", "quantity": 1}]}
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
  }
}
