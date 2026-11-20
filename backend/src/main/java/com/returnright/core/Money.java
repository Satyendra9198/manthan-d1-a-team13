package com.returnright.core;

import com.returnright.core.enums.Grade;

/** Money is always a long of paise (CLAUDE.md money rules). */
public final class Money {

    private Money() {}

    /** Refund for an accepted unit: grade C = 50% half-up = (p + 1) / 2, A/B = 100% [A-10][BR-7]. */
    public static long gradeRefundPaise(long unitPricePaise, Grade grade) {
        if (unitPricePaise <= 0) {
            throw new IllegalArgumentException("unit price must be positive");
        }
        return switch (grade) {
            case A, B -> unitPricePaise;
            case C -> (unitPricePaise + 1) / 2;
        };
    }

    /** Indian grouping, e.g. 12345678 -> "₹1,23,456.78". For messages only; the UI formats its own. */
    public static String formatInr(long paise) {
        String sign = paise < 0 ? "-" : "";
        long abs = Math.abs(paise);
        String rupees = Long.toString(abs / 100);
        StringBuilder sb = new StringBuilder();
        if (rupees.length() > 3) {
            String head = rupees.substring(0, rupees.length() - 3);
            String tail = rupees.substring(rupees.length() - 3);
            StringBuilder h = new StringBuilder();
            while (head.length() > 2) {
                h.insert(0, "," + head.substring(head.length() - 2));
                head = head.substring(0, head.length() - 2);
            }
            h.insert(0, head);
            sb.append(h).append(',').append(tail);
        } else {
            sb.append(rupees);
        }
        return "%s₹%s.%02d".formatted(sign, sb, abs % 100);
    }
}
