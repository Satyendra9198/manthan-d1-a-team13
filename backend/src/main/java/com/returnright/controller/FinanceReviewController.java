package com.returnright.controller;

import com.returnright.model.domain.StaffActor;
import com.returnright.model.dto.request.ReviewNoteRequest;
import com.returnright.model.dto.response.FinanceQueueItemResponse;
import com.returnright.model.dto.response.ReviewDecisionResponse;
import com.returnright.service.ReviewService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Refunds above ₹10,000 waiting for a {@code FINANCE_REVIEWER} (A-08). */
@RestController
@RequestMapping("/api/v1/finance/returns")
public class FinanceReviewController {

  private final ReviewService reviewService;

  public FinanceReviewController(ReviewService reviewService) {
    this.reviewService = reviewService;
  }

  @GetMapping("/pending")
  public List<FinanceQueueItemResponse> pending() {
    return reviewService.refundsAwaitingFinance();
  }

  /** Approves and pays the refund. 409 if the return isn't waiting for finance. */
  @PostMapping("/{rmaNumber}/approve")
  public ReviewDecisionResponse approve(
      @PathVariable String rmaNumber, @AuthenticationPrincipal Jwt token) {
    return reviewService.approveRefund(rmaNumber, StaffActor.from(token));
  }

  /** Declines the refund with a required note. 409 if the return isn't waiting for finance. */
  @PostMapping("/{rmaNumber}/decline")
  public ReviewDecisionResponse decline(
      @PathVariable String rmaNumber,
      @Valid @RequestBody ReviewNoteRequest request,
      @AuthenticationPrincipal Jwt token) {
    return reviewService.declineRefund(rmaNumber, request.note(), StaffActor.from(token));
  }
}
