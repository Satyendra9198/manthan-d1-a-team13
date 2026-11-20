package com.returnright.service.impl;

/**
 * Formats paise for customer messages, Indian style: ₹1,899 or ₹949.50 or ₹1,00,000. The JDK's
 * number formats only support Western grouping, so the lakh grouping is done here.
 */
final class Rupees {

  private Rupees() {}

  static String format(long paise) {
    String sign = paise < 0 ? "-" : "";
    long absolutePaise = Math.abs(paise);
    String rupees = groupIndian(Long.toString(absolutePaise / 100));
    long remainder = absolutePaise % 100;
    String fraction = remainder == 0 ? "" : String.format(".%02d", remainder);
    return sign + "₹" + rupees + fraction;
  }

  /** Last three digits, then groups of two: 1234567 → 12,34,567. */
  private static String groupIndian(String digits) {
    if (digits.length() <= 3) {
      return digits;
    }
    String lastThree = digits.substring(digits.length() - 3);
    String rest = digits.substring(0, digits.length() - 3);
    StringBuilder grouped = new StringBuilder();
    int firstGroup = rest.length() % 2 == 0 ? 2 : 1;
    grouped.append(rest, 0, firstGroup);
    for (int i = firstGroup; i < rest.length(); i += 2) {
      grouped.append(',').append(rest, i, i + 2);
    }
    return grouped + "," + lastThree;
  }
}
