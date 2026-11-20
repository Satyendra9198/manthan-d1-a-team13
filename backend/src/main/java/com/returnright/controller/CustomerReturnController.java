package com.returnright.controller;

import com.returnright.model.domain.PhotoUpload;
import com.returnright.model.dto.request.SubmitReturnRequest;
import com.returnright.model.dto.response.ReturnCreatedResponse;
import com.returnright.service.ReturnSubmissionService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/** Customer wizard, final step: submit the return with its photos. */
@RestController
@RequestMapping("/api/v1/customer/returns")
public class CustomerReturnController {

  static final String RETURN_PART = "return";
  static final String PHOTO_PART_PREFIX = "photos.";

  private final ReturnSubmissionService returnSubmissionService;

  public CustomerReturnController(ReturnSubmissionService returnSubmissionService) {
    this.returnSubmissionService = returnSubmissionService;
  }

  /**
   * Multipart request: a {@code return} part with JSON content type, plus zero or more image parts
   * named {@code photos.<itemId>}.
   *
   * @return 201 with the RMA, 400 if the request is malformed, 404 if no order matches, 413 if a
   *     file is too large, 422 if an item or photo can't be accepted
   */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ReturnCreatedResponse> submitReturn(
      @Valid @RequestPart(RETURN_PART) SubmitReturnRequest request,
      MultipartHttpServletRequest multipartRequest) {
    ReturnCreatedResponse created =
        returnSubmissionService.submit(request, extractPhotos(multipartRequest));
    return ResponseEntity.created(URI.create("/api/v1/customer/returns/" + created.rmaNumber()))
        .body(created);
  }

  private static List<PhotoUpload> extractPhotos(MultipartHttpServletRequest multipartRequest) {
    List<PhotoUpload> photos = new ArrayList<>();
    for (Map.Entry<String, List<MultipartFile>> part :
        multipartRequest.getMultiFileMap().entrySet()) {
      String partName = part.getKey();
      if (partName.equals(RETURN_PART)) {
        continue;
      }
      UUID itemId = itemIdFromPartName(partName);
      for (MultipartFile file : part.getValue()) {
        photos.add(new PhotoUpload(itemId, readBytes(file)));
      }
    }
    return photos;
  }

  private static UUID itemIdFromPartName(String partName) {
    if (!partName.startsWith(PHOTO_PART_PREFIX)) {
      throw new IllegalArgumentException(
          "Unexpected part '" + partName + "'; photo parts must be named photos.<itemId>");
    }
    try {
      return UUID.fromString(partName.substring(PHOTO_PART_PREFIX.length()));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(
          "Photo part '" + partName + "' does not end with a valid item id", ex);
    }
  }

  private static byte[] readBytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException ex) {
      throw new UncheckedIOException("Could not read uploaded photo", ex);
    }
  }
}
