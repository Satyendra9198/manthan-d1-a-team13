package com.returnright.returns;

import com.returnright.labels.service.LabelService;
import com.returnright.returns.repository.ReturnRepository;
import com.returnright.returns.service.ReturnStateMachine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.returnright.audit.service.AuditService;
import com.returnright.core.infra.Metrics;
import com.returnright.core.infra.OutboxService;
import com.returnright.core.config.RrProperties;
import com.returnright.core.enums.ReturnEvent;
import com.returnright.core.enums.ReturnStatus;
import com.returnright.labels.repository.ReturnLabelRepository;
import com.returnright.notifications.service.NotificationService;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Part 1 §4.3 transition table T2-T17, and its parity with the V2 DB trigger [BR-6]
 * (the "TransitionTableParityTest" that ReturnStateMachine refers to).
 */
class TransitionTableTest {

    private ReturnStateMachine sm;

    @BeforeEach
    void setUp() {
        sm = new ReturnStateMachine(mock(ReturnRepository.class), mock(EntityManager.class), mock(LabelService.class),
                mock(NotificationService.class), mock(AuditService.class), mock(OutboxService.class),
                mock(Metrics.class), Clock.systemUTC(), mock(RrProperties.class));
    }

    @Test
    void javaTableMatchesTheDatabaseTriggerExactly() throws IOException {
        assertThat(sm.allowedPairs()).isEqualTo(triggerPairs());
    }

    @Test
    void tableHasTheDocumentedStatusChangingPairs() {
        assertThat(sm.allowedPairs()).containsExactlyInAnyOrder(
                "REQUESTED>APPROVED", "REQUESTED>CANCELLED",
                "APPROVED>IN_TRANSIT", "APPROVED>RECEIVED", "APPROVED>CANCELLED",
                "IN_TRANSIT>RECEIVED",
                "RECEIVED>INSPECTED",
                "INSPECTED>REJECTED", "INSPECTED>PENDING_FINANCE", "INSPECTED>REFUND_PENDING",
                "PENDING_FINANCE>REFUND_PENDING", "PENDING_FINANCE>REJECTED",
                "REFUND_PENDING>REFUNDED", "REFUND_PENDING>REFUND_FAILED",
                "REFUND_FAILED>REFUND_PENDING");
    }

    @ParameterizedTest(name = "{0} + {1} is legal")
    @CsvSource({
        "REQUESTED, AUTO_APPROVE",        // T2
        "APPROVED, MARK_SHIPPED",         // T3
        "APPROVED, SCAN_RECEIVE",         // T4 (carrier scan missed)
        "IN_TRANSIT, SCAN_RECEIVE",       // T4
        "REQUESTED, CANCEL",              // T5
        "APPROVED, CANCEL",               // T5
        "APPROVED, LABEL_EXPIRE",         // T6
        "RECEIVED, INSPECT_ITEM",         // T7 (self-loop)
        "RECEIVED, REGRADE_ITEM",         // T8 (self-loop)
        "RECEIVED, COMPLETE_INSPECTION",  // T9
        "INSPECTED, ROUTE",               // T10-T12
        "PENDING_FINANCE, FINANCE_APPROVE", // T13
        "PENDING_FINANCE, FINANCE_REJECT",  // T14
        "REFUND_PENDING, REFUND_SUCCEEDED", // T15
        "REFUND_PENDING, REFUND_FAILED",    // T16
        "REFUND_FAILED, RETRY_REFUND",      // T17
    })
    void legalTransitions(ReturnStatus from, ReturnEvent event) {
        assertThat(sm.hasTransition(from, event)).isTrue();
    }

    @ParameterizedTest(name = "{0} + {1} is illegal")
    @CsvSource({
        "CANCELLED, SCAN_RECEIVE",          // F-05: scanning a cancelled return
        "INSPECTED, SCAN_RECEIVE",          // F-05: scanning an already-inspected return
        "RECEIVED, SCAN_RECEIVE",           // F-06: double scan is not a transition
        "IN_TRANSIT, CANCEL",               // [A-12] no cancel once shipped
        "REFUNDED, RETRY_REFUND",
        "REFUND_PENDING, RETRY_REFUND",     // no human retry while a refund is in flight
        "REQUESTED, FINANCE_APPROVE",
        "REFUNDED, FINANCE_APPROVE",        // F-04: second approval after the first won
        "REJECTED, CANCEL",
        "RECEIVED, ROUTE",                  // must complete inspection first
        "APPROVED, INSPECT_ITEM",
    })
    void illegalTransitions(ReturnStatus from, ReturnEvent event) {
        assertThat(sm.hasTransition(from, event)).isFalse();
    }

    @Test
    void terminalStatesHaveNoOutgoingTransitions() {
        for (ReturnStatus s : ReturnStatus.values()) {
            if (!s.isTerminal()) {
                continue;
            }
            for (ReturnEvent e : ReturnEvent.values()) {
                assertThat(sm.hasTransition(s, e)).as("%s + %s", s, e).isFalse();
            }
        }
    }

    /** The status pairs listed in rr_returns_valid_transition() in V2__business_rules.sql. */
    private static Set<String> triggerPairs() throws IOException {
        String sql;
        try (InputStream in = TransitionTableTest.class.getResourceAsStream("/db/migration/V2__business_rules.sql")) {
            assertThat(in).as("V2 migration on the classpath").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher fn = Pattern.compile("FUNCTION rr_returns_valid_transition\\(\\).*?NOT IN \\((.*?)\\) THEN",
                Pattern.DOTALL).matcher(sql);
        assertThat(fn.find()).as("trigger function found").isTrue();
        Set<String> pairs = new TreeSet<>();
        Matcher m = Pattern.compile("'([A-Z_]+>[A-Z_]+)'").matcher(fn.group(1));
        while (m.find()) {
            pairs.add(m.group(1));
        }
        return pairs;
    }
}
