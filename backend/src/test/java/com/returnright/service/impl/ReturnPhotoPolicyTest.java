package com.returnright.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.returnright.exception.InvalidPhotosException;
import com.returnright.model.domain.PhotoUpload;
import com.returnright.model.domain.ValidatedPhoto;
import com.returnright.model.dto.request.ReturnItemRequest;
import com.returnright.model.enums.PhotoFormat;
import com.returnright.model.enums.ReturnReason;
import com.returnright.support.TestImages;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ReturnPhotoPolicyTest {

  private static final UUID SHIRT_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
  private static final UUID MUGS_ID = UUID.fromString("11111111-0000-0000-0000-000000000002");

  private final ReturnPhotoPolicy policy = new ReturnPhotoPolicy();

  @Test
  void acceptedPhotosAreGroupedByItemWithTheirDetectedFormat() {
    Map<UUID, List<ValidatedPhoto>> photosByItem =
        policy.validate(
            List.of(item(SHIRT_ID, ReturnReason.TOO_SMALL), item(MUGS_ID, ReturnReason.TOO_BIG)),
            List.of(
                new PhotoUpload(SHIRT_ID, TestImages.JPEG),
                new PhotoUpload(SHIRT_ID, TestImages.WEBP),
                new PhotoUpload(MUGS_ID, TestImages.PNG)));

    assertThat(photosByItem.get(SHIRT_ID))
        .extracting(ValidatedPhoto::format)
        .containsExactly(PhotoFormat.JPEG, PhotoFormat.WEBP);
    assertThat(photosByItem.get(MUGS_ID))
        .extracting(ValidatedPhoto::format)
        .containsExactly(PhotoFormat.PNG);
  }

  @Test
  void itemWithoutPhotosMapsToAnEmptyList() {
    Map<UUID, List<ValidatedPhoto>> photosByItem =
        policy.validate(List.of(item(SHIRT_ID, ReturnReason.CHANGED_MY_MIND)), List.of());

    assertThat(photosByItem.get(SHIRT_ID)).isEmpty();
  }

  @Test
  void photoForAnItemNotOnTheReturnIsRejected() {
    assertThatThrownBy(
            () ->
                policy.validate(
                    List.of(item(SHIRT_ID, ReturnReason.TOO_SMALL)),
                    List.of(new PhotoUpload(MUGS_ID, TestImages.JPEG))))
        .isInstanceOfSatisfying(
            InvalidPhotosException.class,
            ex ->
                assertThat(ex.getProblems())
                    .containsExactly(
                        MUGS_ID + ": Photos can only be added for items you are returning."));
  }

  @Test
  void fivePhotosForOneItemAreAccepted() {
    Map<UUID, List<ValidatedPhoto>> photosByItem =
        policy.validate(List.of(item(SHIRT_ID, ReturnReason.TOO_SMALL)), jpegs(SHIRT_ID, 5));

    assertThat(photosByItem.get(SHIRT_ID)).hasSize(5);
  }

  @Test
  void sixthPhotoForOneItemIsRejectedOnce() {
    assertThatThrownBy(
            () ->
                policy.validate(
                    List.of(item(SHIRT_ID, ReturnReason.TOO_SMALL)), jpegs(SHIRT_ID, 7)))
        .isInstanceOfSatisfying(
            InvalidPhotosException.class,
            ex ->
                assertThat(ex.getProblems())
                    .containsExactly(SHIRT_ID + ": Add at most 5 photos per item."));
  }

  @Test
  void pdfNamedLikeAnImageIsRejected() {
    assertThatThrownBy(
            () ->
                policy.validate(
                    List.of(item(SHIRT_ID, ReturnReason.TOO_SMALL)),
                    List.of(new PhotoUpload(SHIRT_ID, TestImages.PDF))))
        .isInstanceOfSatisfying(
            InvalidPhotosException.class,
            ex ->
                assertThat(ex.getProblems())
                    .containsExactly(SHIRT_ID + ": Photos must be JPEG, PNG or WebP images."));
  }

  @Test
  void emptyFileIsRejected() {
    assertThatThrownBy(
            () ->
                policy.validate(
                    List.of(item(SHIRT_ID, ReturnReason.TOO_SMALL)),
                    List.of(new PhotoUpload(SHIRT_ID, new byte[0]))))
        .isInstanceOfSatisfying(
            InvalidPhotosException.class,
            ex ->
                assertThat(ex.getProblems())
                    .containsExactly(SHIRT_ID + ": One of the photos is empty."));
  }

  @Test
  void photoOfExactly5MbIsAccepted() {
    Map<UUID, List<ValidatedPhoto>> photosByItem =
        policy.validate(
            List.of(item(SHIRT_ID, ReturnReason.TOO_SMALL)),
            List.of(new PhotoUpload(SHIRT_ID, TestImages.jpegOfSize(5 * 1024 * 1024))));

    assertThat(photosByItem.get(SHIRT_ID)).hasSize(1);
  }

  @Test
  void photoLargerThan5MbIsRejected() {
    assertThatThrownBy(
            () ->
                policy.validate(
                    List.of(item(SHIRT_ID, ReturnReason.TOO_SMALL)),
                    List.of(new PhotoUpload(SHIRT_ID, TestImages.jpegOfSize(5 * 1024 * 1024 + 1)))))
        .isInstanceOfSatisfying(
            InvalidPhotosException.class,
            ex ->
                assertThat(ex.getProblems())
                    .containsExactly(SHIRT_ID + ": Each photo must be 5 MB or smaller."));
  }

  @ParameterizedTest
  @EnumSource(
      value = ReturnReason.class,
      names = {"DAMAGED_ON_ARRIVAL", "NOT_AS_DESCRIBED"})
  void faultReasonWithoutAPhotoIsRejected(ReturnReason reason) {
    assertThatThrownBy(() -> policy.validate(List.of(item(SHIRT_ID, reason)), List.of()))
        .isInstanceOfSatisfying(
            InvalidPhotosException.class,
            ex ->
                assertThat(ex.getProblems())
                    .singleElement()
                    .asString()
                    .startsWith(SHIRT_ID + ": Add at least one photo")
                    .contains(reason.getDisplayName()));
  }

  @ParameterizedTest
  @EnumSource(
      value = ReturnReason.class,
      names = {"TOO_BIG", "TOO_SMALL", "CHANGED_MY_MIND"})
  void reasonWithoutAFaultNeedsNoPhoto(ReturnReason reason) {
    assertThat(policy.validate(List.of(item(SHIRT_ID, reason)), List.of()).get(SHIRT_ID)).isEmpty();
  }

  @Test
  void faultReasonWithOnlyAnInvalidPhotoReportsTheInvalidPhoto() {
    assertThatThrownBy(
            () ->
                policy.validate(
                    List.of(item(SHIRT_ID, ReturnReason.DAMAGED_ON_ARRIVAL)),
                    List.of(new PhotoUpload(SHIRT_ID, TestImages.PDF))))
        .isInstanceOfSatisfying(
            InvalidPhotosException.class,
            ex ->
                assertThat(ex.getProblems())
                    .containsExactly(SHIRT_ID + ": Photos must be JPEG, PNG or WebP images."));
  }

  @Test
  void everyProblemIsReportedTogether() {
    assertThatThrownBy(
            () ->
                policy.validate(
                    List.of(
                        item(SHIRT_ID, ReturnReason.DAMAGED_ON_ARRIVAL),
                        item(MUGS_ID, ReturnReason.TOO_BIG)),
                    List.of(new PhotoUpload(MUGS_ID, TestImages.PDF))))
        .isInstanceOfSatisfying(
            InvalidPhotosException.class, ex -> assertThat(ex.getProblems()).hasSize(2));
  }

  private static ReturnItemRequest item(UUID itemId, ReturnReason reason) {
    return new ReturnItemRequest(itemId, 1, reason, null);
  }

  private static List<PhotoUpload> jpegs(UUID itemId, int count) {
    return Collections.nCopies(count, new PhotoUpload(itemId, TestImages.JPEG));
  }
}
