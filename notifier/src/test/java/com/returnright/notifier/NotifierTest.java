package com.returnright.notifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class NotifierTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static Map<String, Object> envelope(long eventId, String toState, String method) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rma_number", "RMA-26-001234");
        payload.put("order_number", "RR-EDGE-001");
        payload.put("customer_email", "edge.customer@example.com");
        payload.put("customer_name", "Asha Verma");
        payload.put("to_state", toState);
        payload.put("approved_refund_paise", 1_249_950L);
        payload.put("refund_method", method);
        return Map.of("event_id", eventId, "event_type", "ReturnStatusChanged", "payload", payload);
    }

    // ---------------------------------------------------------------------------------------- composer

    @ParameterizedTest(name = "{0} -> mail")
    @CsvSource({"APPROVED, is approved", "RECEIVED, received your return", "REFUNDED, refund for",
            "REJECTED, Update on your return", "CANCELLED, was cancelled"})
    void customerMilestonesSendMail(String state, String subjectPart) {
        var email = EmailComposer.compose(envelope(1, state, "ORIGINAL_PAYMENT")).orElseThrow();
        assertThat(email.to()).isEqualTo("edge.customer@example.com");
        assertThat(email.subject()).contains(subjectPart).contains("RMA-26-001234");
        assertThat(email.body()).startsWith("Hi Asha,").contains("RR-EDGE-001");
    }

    @ParameterizedTest(name = "{0} -> no mail")
    @CsvSource({"REQUESTED", "IN_TRANSIT", "INSPECTED", "PENDING_FINANCE", "REFUND_PENDING", "REFUND_FAILED"})
    void internalStatesSendNothing(String state) {
        assertThat(EmailComposer.compose(envelope(1, state, "ORIGINAL_PAYMENT"))).isEmpty();
    }

    @Test
    void refundMailNamesTheAmountAndMethod() {
        assertThat(EmailComposer.compose(envelope(1, "REFUNDED", "ORIGINAL_PAYMENT")).orElseThrow().body())
                .contains("₹12,499.50").contains("original payment method");
        assertThat(EmailComposer.compose(envelope(1, "REFUNDED", "STORE_CREDIT")).orElseThrow().body())
                .contains("store credit");
    }

    @Test
    void otherEventTypesAndBadPayloadsAreIgnored() {
        assertThat(EmailComposer.compose(Map.of("event_type", "SomethingElse", "payload", Map.of()))).isEmpty();
        assertThat(EmailComposer.compose(Map.of("event_type", "ReturnStatusChanged"))).isEmpty();
        assertThat(EmailComposer.compose(Map.of("event_type", "ReturnStatusChanged",
                "payload", Map.of("to_state", "APPROVED")))).isEmpty();   // no recipient
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"0|₹0.00", "99900|₹999.00", "1000000|₹10,000.00", "12345678|₹1,23,456.78"})
    void formatsRupeesWithIndianGrouping(long paise, String expected) {
        assertThat(EmailComposer.inr(paise)).isEqualTo(expected);
    }

    // ---------------------------------------------------------------------------------------- listener

    @Test
    void duplicateDeliveriesSendOneEmail() {
        JavaMailSender mail = mock(JavaMailSender.class);
        ReturnEventListener listener = new ReturnEventListener(mail, new SimpleMeterRegistry(), "rr@test");
        String msg = JSON.writeValueAsString(envelope(42, "APPROVED", "ORIGINAL_PAYMENT"));

        listener.onEvent(msg);
        listener.onEvent(msg);   // Kafka at-least-once redelivery

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, times(1)).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly("edge.customer@example.com");
        assertThat(sent.getValue().getFrom()).isEqualTo("rr@test");
    }

    @Test
    void failedSendIsNotMarkedSeenSoRedeliveryRetries() {
        JavaMailSender mail = mock(JavaMailSender.class);
        doThrow(new MailSendException("smtp down")).doNothing().when(mail).send(any(SimpleMailMessage.class));
        ReturnEventListener listener = new ReturnEventListener(mail, new SimpleMeterRegistry(), "rr@test");
        String msg = JSON.writeValueAsString(envelope(7, "REFUNDED", "ORIGINAL_PAYMENT"));

        assertThatThrownBy(() -> listener.onEvent(msg)).isInstanceOf(MailSendException.class);
        listener.onEvent(msg);

        verify(mail, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void eventsWithoutAMailAreStillDeduplicatedQuietly() {
        JavaMailSender mail = mock(JavaMailSender.class);
        ReturnEventListener listener = new ReturnEventListener(mail, new SimpleMeterRegistry(), "rr@test");
        listener.onEvent(JSON.writeValueAsString(envelope(9, "IN_TRANSIT", "ORIGINAL_PAYMENT")));
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }
}
