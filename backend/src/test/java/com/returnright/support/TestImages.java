package com.returnright.support;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Smallest byte sequences that carry each format's signature. */
public final class TestImages {

  public static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};

  public static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0};

  public static final byte[] WEBP = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);

  public static final byte[] PDF = "%PDF-1.7 not an image".getBytes(StandardCharsets.US_ASCII);

  private TestImages() {}

  /** A JPEG of exactly {@code size} bytes. */
  public static byte[] jpegOfSize(int size) {
    return Arrays.copyOf(JPEG, size);
  }
}
