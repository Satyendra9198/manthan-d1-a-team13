package com.returnright.notifier;

import java.util.Map;
import java.util.Optional;

/**
 * Turns a ReturnStatusChanged envelope (published by the API's OutboxRelay) into a customer email. Pure: no I/O.
 * Only customer-visible milestones send mail; internal states (PENDING_FINANCE, REFUND_PENDING, ...) do not.
 */
public final class EmailComposer {

    public record Email(String to, String subject, String body) {}

    private EmailComposer() {}

    public static Optional<Email> compose(Map<String, Object> envelope) {
        if (!"ReturnStatusChanged".equals(envelope.get("event_type")) || !(envelope.get("payload") instanceof Map<?, ?> p)) {
            return Optional.empty();
        }
        String to = str(p.get("customer_email"));
        String state = str(p.get("to_state"));
        if (to.isBlank() || state.isBlank()) {
            return Optional.empty();
        }
        String name = str(p.get("customer_name")).isBlank() ? "there" : firstName(str(p.get("customer_name")));
        String rma = str(p.get("rma_number"));
        String order = str(p.get("order_number"));
        String refund = p.get("approved_refund_paise") instanceof Number n ? inr(n.longValue()) : null;
        boolean storeCredit = "STORE_CREDIT".equals(str(p.get("refund_method")));

        String subject;
        String line;
        switch (state) {
            case "APPROVED" -> {
                subject = "Your return %s is approved".formatted(rma);
                line = "Good news: your return for order %s is approved. Print your label from your account and "
                        .formatted(order) + "drop the parcel at any courier partner within 14 days.";
            }
            case "RECEIVED" -> {
                subject = "We've received your return %s".formatted(rma);
                line = "Your parcel has arrived at our returns centre. We'll inspect it and update you shortly.";
            }
            case "REFUNDED" -> {
                subject = "Your refund for %s is on its way".formatted(rma);
                line = (refund == null ? "Your refund" : "Your refund of " + refund) + (storeCredit
                        ? " has been added to your store credit."
                        : " has been sent to your original payment method. Banks usually take 3-5 working days.");
            }
            case "REJECTED" -> {
                subject = "Update on your return %s".formatted(rma);
                line = "After inspection we couldn't accept this return. Open the return in your account to see why.";
            }
            case "CANCELLED" -> {
                subject = "Your return %s was cancelled".formatted(rma);
                line = "Your return request has been cancelled. If this wasn't you, please contact support.";
            }
            default -> {
                return Optional.empty();
            }
        }
        String body = """
                Hi %s,

                %s

                Return: %s
                Order: %s

                - ReturnRight
                """.formatted(name, line, rma, order);
        return Optional.of(new Email(to, subject, body));
    }

    /** Indian grouping, e.g. 12345678 -> "₹1,23,456.78" (same format as the API's Money.formatInr). */
    static String inr(long paise) {
        String rupees = Long.toString(Math.abs(paise) / 100);
        StringBuilder grouped = new StringBuilder();
        if (rupees.length() > 3) {
            String head = rupees.substring(0, rupees.length() - 3);
            StringBuilder h = new StringBuilder();
            while (head.length() > 2) {
                h.insert(0, "," + head.substring(head.length() - 2));
                head = head.substring(0, head.length() - 2);
            }
            grouped.append(head).append(h).append(',').append(rupees.substring(rupees.length() - 3));
        } else {
            grouped.append(rupees);
        }
        return "%s₹%s.%02d".formatted(paise < 0 ? "-" : "", grouped, Math.abs(paise) % 100);
    }

    private static String firstName(String full) {
        int space = full.indexOf(' ');
        return space > 0 ? full.substring(0, space) : full;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }
}
