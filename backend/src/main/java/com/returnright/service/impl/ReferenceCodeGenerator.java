package com.returnright.service.impl;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Random human-friendly codes. Ambiguous characters (0/O, 1/I/L) are left out. */
@Component
public class ReferenceCodeGenerator {

  private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
  private static final int RETURN_NUMBER_LENGTH = 8;
  private static final int LABEL_CODE_LENGTH = 10;

  private final SecureRandom random = new SecureRandom();

  public String returnNumber() {
    return "RET-" + randomCode(RETURN_NUMBER_LENGTH);
  }

  public String labelCode() {
    return "RRL-" + randomCode(LABEL_CODE_LENGTH);
  }

  private String randomCode(int length) {
    StringBuilder code = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
    }
    return code.toString();
  }
}
