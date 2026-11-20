package com.returnright.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.returnright.exception.InvalidPhotosException;
import com.returnright.exception.ItemSelectionRejectedException;
import com.returnright.model.domain.PhotoUpload;
import com.returnright.model.dto.request.ReturnItemRequest;
import com.returnright.model.dto.request.SubmitReturnRequest;
import com.returnright.model.dto.response.ReturnCreatedResponse;
import com.returnright.model.entity.CustomerOrder;
import com.returnright.model.entity.OrderItem;
import com.returnright.model.entity.ReturnItem;
import com.returnright.model.entity.ReturnPhoto;
import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.enums.CustomerTimelineStep;
import com.returnright.model.enums.ProductCategory;
import com.returnright.model.enums.RefundMethod;
import com.returnright.model.enums.ReturnReason;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.repository.CustomerOrderRepository;
import com.returnright.repository.ReturnItemRepository;
import com.returnright.repository.ReturnRequestRepository;
import com.returnright.support.InMemoryPhotoStorage;
import com.returnright.support.TestImages;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

class ReturnSubmissionServiceImplTest {

  private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

  /** 20 Nov 2026, 12:00 IST. */
  private static final Instant NOW = Instant.parse("2026-11-20T06:30:00Z");

  private static final String ORDER_NUMBER = "RR-10482";
  private static final String EMAIL = "aditi.sharma@example.com";

  private final CustomerOrderRepository customerOrderRepository =
      mock(CustomerOrderRepository.class);
  private final ReturnItemRepository returnItemRepository = mock(ReturnItemRepository.class);
  private final ReturnRequestRepository returnRequestRepository =
      mock(ReturnRequestRepository.class);
  private final InMemoryPhotoStorage photoStorage = new InMemoryPhotoStorage();

  private ReturnSubmissionServiceImpl service;
  private CustomerOrder order;

  /** Two linen shirts at ₹1,899, delivered 5 days ago. */
  private OrderItem linenShirts;

  /** One mug set at ₹1,249, delivered 5 days ago. */
  private OrderItem mugSet;

  /** Innerwear, never returnable. */
  private OrderItem briefs;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    CustomerOrderReader reader =
        new CustomerOrderReader(customerOrderRepository, returnItemRepository);
    service =
        new ReturnSubmissionServiceImpl(
            reader,
            new ItemSelectionServiceImpl(reader, new EligibilityServiceImpl(clock)),
            new ReturnPhotoPolicy(),
            new CustomerFlagServiceImpl(clock),
            new ReturnStateMachineServiceImpl(),
            returnRequestRepository,
            photoStorage,
            clock);

    linenShirts = item(1, ProductCategory.APPAREL, "Linen shirt", 189_900, 2);
    mugSet = item(2, ProductCategory.HOME_KITCHEN, "Ceramic mug set", 124_900, 1);
    briefs = item(3, ProductCategory.INNERWEAR, "Cotton briefs", 59_900, 1);
    order =
        new CustomerOrder(
            UUID.randomUUID(), ORDER_NUMBER, EMAIL, "Aditi Sharma", NOW.minus(Duration.ofDays(10)));
    List.of(linenShirts, mugSet, briefs).forEach(order::addItem);

    when(customerOrderRepository.findByOrderNumberAndCustomerEmail(ORDER_NUMBER, EMAIL))
        .thenReturn(Optional.of(order));
    when(customerOrderRepository.lockById(order.getId())).thenReturn(Optional.of(order));
    when(returnRequestRepository.findRequestTimes(eq(EMAIL), eq(ReturnStatus.CANCELLED), any()))
        .thenReturn(List.of());
    when(returnRequestRepository.nextRmaSequence()).thenReturn(731L);
    when(returnRequestRepository.saveAndFlush(any(ReturnRequest.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void customerWithNoRecentReturnsIsApprovedWithTheLabelReady() {
    ReturnCreatedResponse response =
        service.submit(request(line(linenShirts, 1, ReturnReason.TOO_SMALL)), List.of());

    assertThat(response.status()).isEqualTo(CustomerTimelineStep.APPROVED);
    assertThat(response.labelReady()).isTrue();
    assertThat(savedReturn().getApprovedAt()).isEqualTo(NOW);
    assertThat(savedReturn().isFlaggedForReview()).isFalse();
  }

  @Test
  void customerWithThreeEarlierRequestsIn30DaysStaysRequestedForReview() {
    when(returnRequestRepository.findRequestTimes(eq(EMAIL), eq(ReturnStatus.CANCELLED), any()))
        .thenReturn(
            List.of(
                NOW.minus(Duration.ofDays(2)),
                NOW.minus(Duration.ofDays(10)),
                NOW.minus(Duration.ofDays(25))));

    ReturnCreatedResponse response =
        service.submit(request(line(linenShirts, 1, ReturnReason.TOO_SMALL)), List.of());

    assertThat(response.status()).isEqualTo(CustomerTimelineStep.REQUESTED);
    assertThat(response.labelReady()).isFalse();
    assertThat(savedReturn().isFlaggedForReview()).isTrue();
    assertThat(savedReturn().getApprovedAt()).isNull();
  }

  @Test
  void flagLookbackStarts30DaysBeforeNowAndIgnoresCancelledReturns() {
    service.submit(request(line(linenShirts, 1, ReturnReason.TOO_SMALL)), List.of());

    verify(returnRequestRepository)
        .findRequestTimes(EMAIL, ReturnStatus.CANCELLED, NOW.minus(Duration.ofDays(30)));
  }

  @Test
  void rmaNumberCombinesTheIndianYearAndAFiveDigitSequence() {
    ReturnCreatedResponse response =
        service.submit(request(line(linenShirts, 1, ReturnReason.TOO_SMALL)), List.of());

    assertThat(response.rmaNumber()).isEqualTo("RMA-2026-00731");
  }

  @Test
  void savedReturnHoldsEachItemWithQuantityReasonAndComment() {
    service.submit(
        request(
            new ReturnItemRequest(
                linenShirts.getId(), 2, ReturnReason.TOO_SMALL, "  Ordered L, fits like M.  "),
            new ReturnItemRequest(mugSet.getId(), 1, ReturnReason.CHANGED_MY_MIND, "   ")),
        List.of());

    ReturnRequest saved = savedReturn();
    assertThat(saved.getOrder()).isSameAs(order);
    assertThat(saved.getRefundMethod()).isEqualTo(RefundMethod.ORIGINAL_PAYMENT);
    assertThat(saved.getCreatedAt()).isEqualTo(NOW);
    assertThat(saved.getItems())
        .extracting(
            returnItem -> returnItem.getOrderItem().getId(),
            ReturnItem::getQuantity,
            ReturnItem::getReason,
            ReturnItem::getCustomerComment)
        .containsExactly(
            tuple(linenShirts.getId(), 2, ReturnReason.TOO_SMALL, "Ordered L, fits like M."),
            tuple(mugSet.getId(), 1, ReturnReason.CHANGED_MY_MIND, null));
  }

  @Test
  void photosAreStoredUnderServerGeneratedKeysAndLinkedToTheirItem() {
    service.submit(
        request(
            line(linenShirts, 1, ReturnReason.DAMAGED_ON_ARRIVAL),
            line(mugSet, 1, ReturnReason.TOO_BIG)),
        List.of(
            new PhotoUpload(linenShirts.getId(), TestImages.JPEG),
            new PhotoUpload(linenShirts.getId(), TestImages.PNG)));

    ReturnRequest saved = savedReturn();
    List<ReturnPhoto> shirtPhotos = saved.getItems().get(0).getPhotos();
    assertThat(shirtPhotos)
        .extracting(ReturnPhoto::getContentType)
        .containsExactly("image/jpeg", "image/png");
    assertThat(shirtPhotos)
        .allSatisfy(
            photo ->
                assertThat(photo.getStorageKey())
                    .startsWith("returns/" + saved.getId() + "/" + photo.getId()));
    assertThat(saved.getItems().get(1).getPhotos()).isEmpty();
    assertThat(photoStorage.storedPhotos())
        .containsOnlyKeys(shirtPhotos.stream().map(ReturnPhoto::getStorageKey).toList());
  }

  @Test
  void responseReportsTheEstimatedRefundAndPhotoCountPerLine() {
    ReturnCreatedResponse response =
        service.submit(
            request(
                line(linenShirts, 2, ReturnReason.DAMAGED_ON_ARRIVAL),
                line(mugSet, 1, ReturnReason.TOO_BIG)),
            List.of(new PhotoUpload(linenShirts.getId(), TestImages.JPEG)));

    assertThat(response.estimatedRefundPaise()).isEqualTo(2 * 189_900 + 124_900);
    assertThat(response.items())
        .extracting(line -> line.productName(), line -> line.photoCount())
        .containsExactly(tuple("Linen shirt", 1), tuple("Ceramic mug set", 0));
  }

  @Test
  void orderIsLockedBeforeTheReturnIsSaved() {
    service.submit(request(line(linenShirts, 1, ReturnReason.TOO_SMALL)), List.of());

    InOrder inOrder = inOrder(customerOrderRepository, returnRequestRepository);
    inOrder.verify(customerOrderRepository).lockById(order.getId());
    inOrder.verify(returnRequestRepository).saveAndFlush(any(ReturnRequest.class));
  }

  @Test
  void ineligibleItemRejectsTheWholeSubmissionAndSavesNothing() {
    assertThatThrownBy(
            () ->
                service.submit(
                    request(
                        line(linenShirts, 1, ReturnReason.TOO_SMALL),
                        line(briefs, 1, ReturnReason.CHANGED_MY_MIND)),
                    List.of(new PhotoUpload(linenShirts.getId(), TestImages.JPEG))))
        .isInstanceOf(ItemSelectionRejectedException.class);

    verify(returnRequestRepository, never()).saveAndFlush(any());
    verify(returnRequestRepository, never()).nextRmaSequence();
    assertThat(photoStorage.storedPhotos()).isEmpty();
  }

  @Test
  void invalidPhotoRejectsTheWholeSubmissionAndSavesNothing() {
    assertThatThrownBy(
            () ->
                service.submit(
                    request(line(linenShirts, 1, ReturnReason.DAMAGED_ON_ARRIVAL)),
                    List.of(new PhotoUpload(linenShirts.getId(), TestImages.PDF))))
        .isInstanceOf(InvalidPhotosException.class);

    verify(returnRequestRepository, never()).saveAndFlush(any());
    assertThat(photoStorage.storedPhotos()).isEmpty();
  }

  @Test
  void storedPhotosAreDeletedWhenSavingTheReturnFails() {
    when(returnRequestRepository.saveAndFlush(any(ReturnRequest.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate rma_number"));

    assertThatThrownBy(
            () ->
                service.submit(
                    request(line(linenShirts, 1, ReturnReason.DAMAGED_ON_ARRIVAL)),
                    List.of(new PhotoUpload(linenShirts.getId(), TestImages.JPEG))))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(photoStorage.storedPhotos()).isEmpty();
  }

  private ReturnRequest savedReturn() {
    ArgumentCaptor<ReturnRequest> captor = ArgumentCaptor.forClass(ReturnRequest.class);
    verify(returnRequestRepository).saveAndFlush(captor.capture());
    return captor.getValue();
  }

  private static SubmitReturnRequest request(ReturnItemRequest... items) {
    return new SubmitReturnRequest(
        ORDER_NUMBER, EMAIL, List.of(items), RefundMethod.ORIGINAL_PAYMENT, true);
  }

  private static ReturnItemRequest line(OrderItem item, int quantity, ReturnReason reason) {
    return new ReturnItemRequest(item.getId(), quantity, reason, null);
  }

  private static OrderItem item(
      int lineNumber,
      ProductCategory category,
      String productName,
      long unitPricePaise,
      int quantity) {
    Instant deliveredFiveDaysAgo =
        LocalDate.of(2026, 11, 15).atTime(LocalTime.of(10, 0)).atZone(IST).toInstant();
    return new OrderItem(
        UUID.randomUUID(),
        lineNumber,
        "SKU-" + lineNumber,
        productName,
        null,
        category,
        unitPricePaise,
        quantity,
        deliveredFiveDaysAgo);
  }
}
