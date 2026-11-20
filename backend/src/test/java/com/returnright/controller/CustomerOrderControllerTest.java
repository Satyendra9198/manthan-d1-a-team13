package com.returnright.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.returnright.config.ClockConfig;
import com.returnright.exception.OrderNotFoundException;
import com.returnright.model.dto.request.OrderLookupRequest;
import com.returnright.model.dto.response.OrderItemResponse;
import com.returnright.model.dto.response.OrderLookupResponse;
import com.returnright.model.enums.EligibilityReason;
import com.returnright.model.enums.ProductCategory;
import com.returnright.service.OrderLookupService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerOrderController.class)
@Import(ClockConfig.class)
class CustomerOrderControllerTest {

  private static final String LOOKUP_URL = "/api/v1/customer/orders/lookup";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private OrderLookupService orderLookupService;

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
}
