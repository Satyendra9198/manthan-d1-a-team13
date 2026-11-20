package com.returnright.exception;

import java.util.UUID;

/** The photo does not belong to the return, or its file is missing from storage. */
public class PhotoNotFoundException extends RuntimeException {

  public PhotoNotFoundException(UUID photoId) {
    super("No photo " + photoId + " on this return.");
  }
}
