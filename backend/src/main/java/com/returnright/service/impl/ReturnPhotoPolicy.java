package com.returnright.service.impl;

import com.returnright.exception.InvalidPhotosException;
import com.returnright.model.domain.PhotoUpload;
import com.returnright.model.domain.ValidatedPhoto;
import com.returnright.model.dto.request.ReturnItemRequest;
import com.returnright.model.enums.PhotoFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Photo rules for a return (assumption A-19): JPEG, PNG or WebP only, detected from the bytes; at
 * most {@value #MAX_PHOTOS_PER_ITEM} per item and {@value #MAX_PHOTO_BYTES} bytes each; photos only
 * for items on the return; at least one photo when the reason claims a fault.
 */
@Component
public class ReturnPhotoPolicy {

  static final int MAX_PHOTOS_PER_ITEM = 5;
  static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;

  /**
   * Checks every photo and groups the accepted ones by item.
   *
   * @return accepted photos per selected item; items without photos map to an empty list
   * @throws InvalidPhotosException listing every problem found
   */
  public Map<UUID, List<ValidatedPhoto>> validate(
      List<ReturnItemRequest> items, List<PhotoUpload> photos) {
    Map<UUID, List<ValidatedPhoto>> photosByItem = new LinkedHashMap<>();
    items.forEach(item -> photosByItem.put(item.itemId(), new ArrayList<>()));

    List<String> problems = new ArrayList<>();
    Map<UUID, Integer> photoCounts = new HashMap<>();

    for (PhotoUpload photo : photos) {
      List<ValidatedPhoto> itemPhotos = photosByItem.get(photo.itemId());
      if (itemPhotos == null) {
        problems.add(photo.itemId() + ": Photos can only be added for items you are returning.");
        continue;
      }
      int count = photoCounts.merge(photo.itemId(), 1, Integer::sum);
      if (count == MAX_PHOTOS_PER_ITEM + 1) {
        problems.add(photo.itemId() + ": Add at most " + MAX_PHOTOS_PER_ITEM + " photos per item.");
      }
      checkContent(photo).ifPresentOrElse(problems::add, () -> addValidated(photo, itemPhotos));
    }

    for (ReturnItemRequest item : items) {
      if (item.reason().isPhotoRequired() && photoCounts.getOrDefault(item.itemId(), 0) == 0) {
        problems.add(
            item.itemId()
                + ": Add at least one photo when the reason is \""
                + item.reason().getDisplayName()
                + "\".");
      }
    }

    if (!problems.isEmpty()) {
      throw new InvalidPhotosException(problems);
    }
    return photosByItem;
  }

  private static Optional<String> checkContent(PhotoUpload photo) {
    if (photo.content().length == 0) {
      return Optional.of(photo.itemId() + ": One of the photos is empty.");
    }
    if (photo.content().length > MAX_PHOTO_BYTES) {
      return Optional.of(photo.itemId() + ": Each photo must be 5 MB or smaller.");
    }
    if (PhotoFormat.detect(photo.content()).isEmpty()) {
      return Optional.of(photo.itemId() + ": Photos must be JPEG, PNG or WebP images.");
    }
    return Optional.empty();
  }

  private static void addValidated(PhotoUpload photo, List<ValidatedPhoto> itemPhotos) {
    PhotoFormat format = PhotoFormat.detect(photo.content()).orElseThrow();
    itemPhotos.add(new ValidatedPhoto(format, photo.content()));
  }
}
