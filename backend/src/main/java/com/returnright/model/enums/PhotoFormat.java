package com.returnright.model.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * Accepted photo formats. The format is detected from the file's leading bytes, never from the
 * client's declared content type or file name.
 */
public enum PhotoFormat {
  JPEG("image/jpeg", "jpg"),
  PNG("image/png", "png"),
  WEBP("image/webp", "webp");

  private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
  private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
  private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
  private static final byte[] WEBP_MARKER = {'W', 'E', 'B', 'P'};

  private final String contentType;
  private final String extension;

  PhotoFormat(String contentType, String extension) {
    this.contentType = contentType;
    this.extension = extension;
  }

  public String getContentType() {
    return contentType;
  }

  public String getExtension() {
    return extension;
  }

  /** The format whose signature the content starts with, or empty if none match. */
  public static Optional<PhotoFormat> detect(byte[] content) {
    if (startsWith(content, 0, JPEG_SIGNATURE)) {
      return Optional.of(JPEG);
    }
    if (startsWith(content, 0, PNG_SIGNATURE)) {
      return Optional.of(PNG);
    }
    if (startsWith(content, 0, RIFF) && startsWith(content, 8, WEBP_MARKER)) {
      return Optional.of(WEBP);
    }
    return Optional.empty();
  }

  private static boolean startsWith(byte[] content, int offset, byte[] signature) {
    return content.length >= offset + signature.length
        && Arrays.equals(
            content, offset, offset + signature.length, signature, 0, signature.length);
  }
}
