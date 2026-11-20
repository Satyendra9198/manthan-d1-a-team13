package com.returnright.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.returnright.model.domain.StaffActor;
import com.returnright.model.dto.response.ReviewDecisionResponse;
import com.returnright.model.enums.RefundStatus;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.model.enums.Role;
import com.returnright.service.ReviewService;
import com.returnright.support.WithApiSecurity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest({FinanceReviewController.class, OperationsReviewController.class})
@WithApiSecurity
class ReviewControllersTest {

  private static final String RMA = "RMA-2026-00731";
  private static final UUID STAFF_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ReviewService reviewService;

  @Test
  void financeReviewerSeesThePendingRefunds() throws Exception {
    when(reviewService.refundsAwaitingFinance()).thenReturn(List.of());

    mockMvc
        .perform(get("/api/v1/finance/returns/pending").with(staff(Role.FINANCE_REVIEWER)))
        .andExpect(status().isOk());
  }

  @Test
  void warehouseAgentCannotApproveRefunds() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/finance/returns/" + RMA + "/approve").with(staff(Role.WAREHOUSE_AGENT)))
        .andExpect(status().isForbidden());

    verifyNoInteractions(reviewService);
  }

  @Test
  void financeApprovalIsMadeAsTheSignedInReviewer() throws Exception {
    when(reviewService.approveRefund(eq(RMA), any(StaffActor.class)))
        .thenReturn(new ReviewDecisionResponse(RMA, ReturnStatus.REFUNDED, RefundStatus.SUCCEEDED));

    mockMvc
        .perform(
            post("/api/v1/finance/returns/" + RMA + "/approve").with(staff(Role.FINANCE_REVIEWER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REFUNDED"));

    verify(reviewService).approveRefund(RMA, new StaffActor(STAFF_ID, Role.FINANCE_REVIEWER));
  }

  @Test
  void decliningARefundNeedsANote() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/finance/returns/" + RMA + "/decline")
                .with(staff(Role.FINANCE_REVIEWER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\": \" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[0]").value("note: must not be blank"));

    verifyNoInteractions(reviewService);
  }

  @Test
  void operationsManagerSeesTheFlaggedReturns() throws Exception {
    when(reviewService.flaggedReturns()).thenReturn(List.of());

    mockMvc
        .perform(get("/api/v1/operations/returns/flagged").with(staff(Role.OPERATIONS_MANAGER)))
        .andExpect(status().isOk());
  }

  @Test
  void financeReviewerCannotApproveFlaggedReturns() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/operations/returns/" + RMA + "/approve")
                .with(staff(Role.FINANCE_REVIEWER)))
        .andExpect(status().isForbidden());

    verifyNoInteractions(reviewService);
  }

  @Test
  void operationsDeclineIsPassedOnWithTheNote() throws Exception {
    when(reviewService.declineReturn(eq(RMA), eq("Repeated wear"), any(StaffActor.class)))
        .thenReturn(new ReviewDecisionResponse(RMA, ReturnStatus.REJECTED, null));

    mockMvc
        .perform(
            post("/api/v1/operations/returns/" + RMA + "/decline")
                .with(staff(Role.OPERATIONS_MANAGER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\": \"Repeated wear\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"));
  }

  private static RequestPostProcessor staff(Role role) {
    return jwt()
        .jwt(token -> token.subject(STAFF_ID.toString()).claim("role", role.name()))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }
}
