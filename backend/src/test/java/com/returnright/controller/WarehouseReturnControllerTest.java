package com.returnright.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.returnright.exception.IllegalTransitionException;
import com.returnright.exception.InvalidInspectionException;
import com.returnright.model.domain.PhotoContent;
import com.returnright.model.domain.StaffActor;
import com.returnright.model.dto.request.InspectionRequest;
import com.returnright.model.dto.response.InspectionResultResponse;
import com.returnright.model.dto.response.InspectionSummaryResponse;
import com.returnright.model.enums.RefundStatus;
import com.returnright.model.enums.ReturnEvent;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.model.enums.Role;
import com.returnright.service.WarehouseReturnService;
import com.returnright.support.TestImages;
import com.returnright.support.WithApiSecurity;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(WarehouseReturnController.class)
@WithApiSecurity
class WarehouseReturnControllerTest {

  private static final String BASE = "/api/v1/warehouse";
  private static final String RMA = "RMA-2026-00731";
  private static final UUID AGENT_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
  private static final UUID LINE_ID = UUID.fromString("11111111-bbbb-0000-0000-000000000001");

  private static final String INSPECTION_JSON =
      """
      {"items": [{"returnItemId": "11111111-bbbb-0000-0000-000000000001",
                  "grade": "C", "acceptedQuantity": 1}]}
      """;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private WarehouseReturnService warehouseReturnService;

  @Test
  void requestWithoutATokenIsRejectedAsUnauthorized() throws Exception {
    mockMvc
        .perform(get(BASE + "/returns/queue"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

    verifyNoInteractions(warehouseReturnService);
  }

  @Test
  void financeReviewerCannotUseTheWarehouseScreens() throws Exception {
    mockMvc
        .perform(get(BASE + "/returns/queue").with(staff(Role.FINANCE_REVIEWER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    verifyNoInteractions(warehouseReturnService);
  }

  @Test
  void agentCanSeeTheInspectionQueue() throws Exception {
    when(warehouseReturnService.awaitingInspection()).thenReturn(List.of());

    mockMvc
        .perform(get(BASE + "/returns/queue").with(staff(Role.WAREHOUSE_AGENT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void inspectionIsSavedAsTheSignedInAgent() throws Exception {
    when(warehouseReturnService.completeInspection(
            eq(RMA), any(InspectionRequest.class), any(StaffActor.class)))
        .thenReturn(
            new InspectionResultResponse(
                RMA, ReturnStatus.REFUNDED, 94_950, false, RefundStatus.SUCCEEDED));

    mockMvc
        .perform(
            post(BASE + "/returns/" + RMA + "/inspection")
                .with(staff(Role.WAREHOUSE_AGENT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(INSPECTION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REFUNDED"))
        .andExpect(jsonPath("$.refundAmountPaise").value(94_950));

    ArgumentCaptor<StaffActor> actor = ArgumentCaptor.forClass(StaffActor.class);
    ArgumentCaptor<InspectionRequest> request = ArgumentCaptor.forClass(InspectionRequest.class);
    verify(warehouseReturnService).completeInspection(eq(RMA), request.capture(), actor.capture());
    assertThat(actor.getValue()).isEqualTo(new StaffActor(AGENT_ID, Role.WAREHOUSE_AGENT));
    assertThat(request.getValue().items().get(0).returnItemId()).isEqualTo(LINE_ID);
  }

  @Test
  void inspectionWithoutLinesIsRejectedAsValidationFailed() throws Exception {
    mockMvc
        .perform(
            post(BASE + "/returns/" + RMA + "/inspection")
                .with(staff(Role.WAREHOUSE_AGENT))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\": []}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void inconsistentInspectionIsReportedAsUnprocessable() throws Exception {
    when(warehouseReturnService.completeInspection(
            eq(RMA), any(InspectionRequest.class), any(StaffActor.class)))
        .thenThrow(
            new InvalidInspectionException(List.of(LINE_ID + ": Choose a condition grade.")));

    mockMvc
        .perform(
            post(BASE + "/returns/" + RMA + "/inspection")
                .with(staff(Role.WAREHOUSE_AGENT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(INSPECTION_JSON))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("INVALID_INSPECTION"))
        .andExpect(jsonPath("$.details[0]").value(LINE_ID + ": Choose a condition grade."));
  }

  @Test
  void receivingAParcelTwiceIsReportedAsConflict() throws Exception {
    when(warehouseReturnService.receive(eq(RMA), any(StaffActor.class)))
        .thenThrow(new IllegalTransitionException(ReturnStatus.RECEIVED, ReturnEvent.RECEIVE));

    mockMvc
        .perform(post(BASE + "/returns/" + RMA + "/receive").with(staff(Role.WAREHOUSE_AGENT)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ILLEGAL_TRANSITION"));
  }

  @Test
  void rejectionWithoutAReasonIsRejectedAsValidationFailed() throws Exception {
    mockMvc
        .perform(
            post(BASE + "/returns/" + RMA + "/reject")
                .with(staff(Role.WAREHOUSE_AGENT))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\": \"Worn\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[0]").value("reason: must not be null"));
  }

  @Test
  void photoIsServedAsAnImageThatIsNeverCached() throws Exception {
    UUID photoId = UUID.randomUUID();
    when(warehouseReturnService.photo(RMA, photoId))
        .thenReturn(new PhotoContent("image/jpeg", TestImages.JPEG));

    mockMvc
        .perform(
            get(BASE + "/returns/" + RMA + "/photos/" + photoId).with(staff(Role.WAREHOUSE_AGENT)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_JPEG))
        .andExpect(content().bytes(TestImages.JPEG))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"));
  }

  @Test
  void todaysCounterIsForTheSignedInAgent() throws Exception {
    when(warehouseReturnService.todaySummary(new StaffActor(AGENT_ID, Role.WAREHOUSE_AGENT)))
        .thenReturn(new InspectionSummaryResponse(LocalDate.of(2026, 11, 20), 27, 9));

    mockMvc
        .perform(get(BASE + "/inspections/today").with(staff(Role.WAREHOUSE_AGENT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.inspectedToday").value(27))
        .andExpect(jsonPath("$.inspectedByYouToday").value(9));
  }

  private static RequestPostProcessor staff(Role role) {
    return jwt()
        .jwt(token -> token.subject(AGENT_ID.toString()).claim("role", role.name()))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }
}
