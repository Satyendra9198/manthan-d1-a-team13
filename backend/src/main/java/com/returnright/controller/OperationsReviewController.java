package com.returnright.controller;

import com.returnright.model.domain.StaffActor;
import com.returnright.model.dto.request.ReviewNoteRequest;
import com.returnright.model.dto.response.FlaggedReturnResponse;
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

/** Returns from flagged customers waiting for an {@code OPERATIONS_MANAGER} (A-11). */
@RestController
@RequestMapping("/api/v1/operations/returns")
public class OperationsReviewController {

  private final ReviewService reviewService;

  public OperationsReviewController(ReviewService reviewService) {
    this.reviewService = reviewService;
  }

  @GetMapping("/flagged")
  public List<FlaggedReturnResponse> flagged() {
    return reviewService.flaggedReturns();
  }

  /** Approves the return so the customer's label is ready. 409 if it isn't waiting for review. */
  @PostMapping("/{rmaNumber}/approve")
  public ReviewDecisionResponse approve(
      @PathVariable String rmaNumber, @AuthenticationPrincipal Jwt token) {
    return reviewService.approveReturn(rmaNumber, StaffActor.from(token));
  }

  /** Declines the return with a required note. 409 if it isn't waiting for review. */
  @PostMapping("/{rmaNumber}/decline")
  public ReviewDecisionResponse decline(
      @PathVariable String rmaNumber,
      @Valid @RequestBody ReviewNoteRequest request,
      @AuthenticationPrincipal Jwt token) {
    return reviewService.declineReturn(rmaNumber, request.note(), StaffActor.from(token));
  }
}
