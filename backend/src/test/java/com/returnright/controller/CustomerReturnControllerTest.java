package com.returnright.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.returnright.config.ClockConfig;
import com.returnright.exception.InvalidPhotosException;
import com.returnright.model.domain.PhotoUpload;
import com.returnright.model.dto.request.SubmitReturnRequest;
import com.returnright.model.dto.response.ReturnCreatedResponse;
import com.returnright.model.dto.response.ReturnLineResponse;
import com.returnright.model.enums.CustomerTimelineStep;
import com.returnright.model.enums.RefundMethod;
import com.returnright.model.enums.ReturnReason;
import com.returnright.service.ReturnSubmissionService;
import com.returnright.support.TestImages;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerReturnController.class)
@Import(ClockConfig.class)
class CustomerReturnControllerTest {

  private static final String RETURNS_URL = "/api/v1/customer/returns";
  private static final UUID SHIRT_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");

  private static final String VALID_RETURN_JSON =
      """
      {"orderNumber": "RR-10482", "email": "aditi.sharma@example.com",
       "items": [{"itemId": "11111111-0000-0000-0000-000000000001", "quantity": 1,
                  "reason": "DAMAGED_ON_ARRIVAL", "comment": "Seam torn"}],
       "refundMethod": "ORIGINAL_PAYMENT", "detailsConfirmed": true}
      """;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ReturnSubmissionService returnSubmissionService;

  @Test
  void validSubmissionReturns201WithTheRmaAndItsLocation() throws Exception {
    when(returnSubmissionService.submit(any(SubmitReturnRequest.class), anyList()))
        .thenReturn(created());

    mockMvc
        .perform(
            multipart(RETURNS_URL)
                .file(returnPart(VALID_RETURN_JSON))
                .file(photo("photos." + SHIRT_ID, TestImages.JPEG)))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", RETURNS_URL + "/RMA-2026-00731"))
        .andExpect(jsonPath("$.rmaNumber").value("RMA-2026-00731"))
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.labelReady").value(true))
        .andExpect(jsonPath("$.items[0].photoCount").value(1));
  }

  @Test
  void photosArePassedOnWithTheItemIdFromTheirPartName() throws Exception {
    when(returnSubmissionService.submit(any(SubmitReturnRequest.class), anyList()))
        .thenReturn(created());

    mockMvc
        .perform(
            multipart(RETURNS_URL)
                .file(returnPart(VALID_RETURN_JSON))
                .file(photo("photos." + SHIRT_ID, TestImages.JPEG))
                .file(photo("photos." + SHIRT_ID, TestImages.PNG)))
        .andExpect(status().isCreated());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<PhotoUpload>> photos = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<SubmitReturnRequest> request =
        ArgumentCaptor.forClass(SubmitReturnRequest.class);
    verify(returnSubmissionService).submit(request.capture(), photos.capture());
    assertThat(photos.getValue()).extracting(PhotoUpload::itemId).containsOnly(SHIRT_ID);
    assertThat(photos.getValue())
        .extracting(PhotoUpload::content)
        .containsExactly(TestImages.JPEG, TestImages.PNG);
    assertThat(request.getValue().items().get(0).reason())
        .isEqualTo(ReturnReason.DAMAGED_ON_ARRIVAL);
  }

  @Test
  void missingReturnPartIsRejectedAsMissingPart() throws Exception {
    mockMvc
        .perform(multipart(RETURNS_URL).file(photo("photos." + SHIRT_ID, TestImages.JPEG)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MISSING_PART"));

    verifyNoInteractions(returnSubmissionService);
  }

  @Test
  void unconfirmedDetailsAreRejectedAsValidationFailed() throws Exception {
    mockMvc
        .perform(
            multipart(RETURNS_URL)
                .file(
                    returnPart(
                        VALID_RETURN_JSON.replace(
                            "\"detailsConfirmed\": true", "\"detailsConfirmed\": false"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(
            jsonPath("$.details[0]")
                .value("detailsConfirmed: must confirm the details and photos are accurate"));

    verifyNoInteractions(returnSubmissionService);
  }

  @Test
  void missingReasonIsRejectedAsValidationFailed() throws Exception {
    mockMvc
        .perform(
            multipart(RETURNS_URL)
                .file(
                    returnPart(
                        VALID_RETURN_JSON.replace("\"reason\": \"DAMAGED_ON_ARRIVAL\",", ""))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[0]").value("items[0].reason: must not be null"));

    verifyNoInteractions(returnSubmissionService);
  }

  @Test
  void unknownReasonIsRejectedAsMalformedRequest() throws Exception {
    mockMvc
        .perform(
            multipart(RETURNS_URL)
                .file(returnPart(VALID_RETURN_JSON.replace("DAMAGED_ON_ARRIVAL", "BORED"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

    verifyNoInteractions(returnSubmissionService);
  }

  @Test
  void photoPartWithoutAValidItemIdIsRejectedAsInvalidRequest() throws Exception {
    mockMvc
        .perform(
            multipart(RETURNS_URL)
                .file(returnPart(VALID_RETURN_JSON))
                .file(photo("photos.shirt", TestImages.JPEG)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

    verifyNoInteractions(returnSubmissionService);
  }

  @Test
  void unexpectedFilePartIsRejectedAsInvalidRequest() throws Exception {
    mockMvc
        .perform(
            multipart(RETURNS_URL)
                .file(returnPart(VALID_RETURN_JSON))
                .file(photo("attachment", TestImages.JPEG)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

    verifyNoInteractions(returnSubmissionService);
  }

  @Test
  void invalidPhotosAreReportedAsUnprocessableWithEachProblem() throws Exception {
    when(returnSubmissionService.submit(any(SubmitReturnRequest.class), anyList()))
        .thenThrow(
            new InvalidPhotosException(
                List.of(SHIRT_ID + ": Photos must be JPEG, PNG or WebP images.")));

    mockMvc
        .perform(
            multipart(RETURNS_URL)
                .file(returnPart(VALID_RETURN_JSON))
                .file(photo("photos." + SHIRT_ID, TestImages.PDF)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("INVALID_PHOTOS"))
        .andExpect(jsonPath("$.details.length()").value(1));
  }

  private static MockMultipartFile returnPart(String json) {
    return new MockMultipartFile(
        "return", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
  }

  private static MockMultipartFile photo(String partName, byte[] content) {
    return new MockMultipartFile(partName, "photo.jpg", MediaType.IMAGE_JPEG_VALUE, content);
  }

  private static ReturnCreatedResponse created() {
    return new ReturnCreatedResponse(
        UUID.randomUUID(),
        "RMA-2026-00731",
        CustomerTimelineStep.APPROVED,
        true,
        RefundMethod.ORIGINAL_PAYMENT,
        189_900,
        Instant.parse("2026-11-20T06:30:00Z"),
        List.of(
            new ReturnLineResponse(
                SHIRT_ID, "Linen shirt", 1, ReturnReason.DAMAGED_ON_ARRIVAL, 189_900, 1)));
  }
}
