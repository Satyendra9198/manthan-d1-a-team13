package com.returnright.ledger.service;

import com.returnright.core.web.Correlation;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only double-entry ledger (Part 1 §7). This is the ONLY writer of ledger rows; there is no update path.
 * Every transaction balances (checked here and by the deferred DB trigger [BR-18]).
 */
@Service
public class LedgerService {

    public static final String SALES_RETURNS = "SALES_RETURNS";
    public static final String CONDITION_RECOVERY = "RETURN_CONDITION_RECOVERY";
    public static final String GATEWAY_SETTLEMENT = "GATEWAY_SETTLEMENT";
    public static final String STORE_CREDIT = "STORE_CREDIT_LIABILITY";

    /** One accepted unit: gross = price paid, refund = after grade adjustment. */
    public record UnitLine(UUID returnItemId, long grossPaise, long refundPaise) {}

    public record RefundPosting(UUID returnId, UUID refundAttemptId, long customerId, boolean storeCredit,
                                List<UnitLine> units, String actorId, String memo) {}

    public record Line(String account, String direction, long amountPaise, Long customerId, UUID returnItemId) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public LedgerService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Lines for a refund posting (pure; unit-tested). */
    public static List<Line> refundLines(RefundPosting p) {
        List<Line> lines = new ArrayList<>();
        long total = 0;
        for (UnitLine u : p.units()) {
            if (u.refundPaise() <= 0 || u.refundPaise() > u.grossPaise()) {
                throw new IllegalArgumentException("invalid unit line " + u);
            }
            lines.add(new Line(SALES_RETURNS, "DEBIT", u.grossPaise(), null, u.returnItemId()));
            long retained = u.grossPaise() - u.refundPaise();
            if (retained > 0) { // grade-C deduction made visible [A-10]
                lines.add(new Line(CONDITION_RECOVERY, "CREDIT", retained, null, u.returnItemId()));
            }
            total += u.refundPaise();
        }
        lines.add(p.storeCredit()
                ? new Line(STORE_CREDIT, "CREDIT", total, p.customerId(), null)
                : new Line(GATEWAY_SETTLEMENT, "CREDIT", total, null, null));
        assertBalanced(lines);
        return lines;
    }

    static void assertBalanced(List<Line> lines) {
        long debit = lines.stream().filter(l -> l.direction().equals("DEBIT")).mapToLong(Line::amountPaise).sum();
        long credit = lines.stream().filter(l -> l.direction().equals("CREDIT")).mapToLong(Line::amountPaise).sum();
        if (debit != credit || lines.size() < 2) {
            throw new IllegalStateException("unbalanced ledger posting: debit=" + debit + " credit=" + credit);
        }
    }

    /** Posted only when an attempt reaches SUCCEEDED, at most once per attempt [BR-16]. */
    @Transactional(propagation = Propagation.MANDATORY)
    public long postRefund(RefundPosting p) {
        List<Line> lines = refundLines(p);
        String type = p.storeCredit() ? "REFUND_STORE_CREDIT" : "REFUND_ORIGINAL";
        long txnId = insertTxn(type, p.returnId(), p.refundAttemptId(), null, p.memo(), p.actorId());
        insertLines(txnId, lines);
        return txnId;
    }

    /** Correction = mirror transaction with directions swapped; a transaction can be reversed once [BR-17]. */
    @Transactional(propagation = Propagation.MANDATORY)
    public long reverse(long transactionId, String reason, String actorId) {
        UUID returnId = jdbc.queryForObject("SELECT return_id FROM ledger_transactions WHERE id = ?", UUID.class,
                transactionId);
        List<Line> mirror = jdbc.query("""
                SELECT account_code, direction, amount_paise, customer_id, return_item_id
                FROM ledger_entries WHERE transaction_id = ? ORDER BY id""",
                (rs, i) -> new Line(rs.getString(1), rs.getString(2).equals("DEBIT") ? "CREDIT" : "DEBIT",
                        rs.getLong(3), (Long) rs.getObject(4), rs.getObject(5, UUID.class)), transactionId);
        long id = insertTxn("REVERSAL", returnId, null, transactionId, "Reversal: " + reason, actorId);
        insertLines(id, mirror);
        return id;
    }

    private long insertTxn(String type, UUID returnId, UUID attemptId, Long reverses, String memo, String actor) {
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    INSERT INTO ledger_transactions (txn_type, return_id, refund_attempt_id, reverses_transaction_id,
                      memo, correlation_id, posted_at, posted_by_actor) VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                    new String[] {"id"});
            ps.setString(1, type);
            ps.setObject(2, returnId);
            ps.setObject(3, attemptId);
            ps.setObject(4, reverses);
            ps.setString(5, memo);
            ps.setString(6, Correlation.id());
            ps.setTimestamp(7, Timestamp.from(clock.instant()));
            ps.setString(8, actor);
            return ps;
        }, kh);
        return kh.getKey().longValue();
    }

    private void insertLines(long txnId, List<Line> lines) {
        for (Line l : lines) {
            jdbc.update("""
                    INSERT INTO ledger_entries (transaction_id, account_code, direction, amount_paise, customer_id, return_item_id)
                    VALUES (?, ?, ?, ?, ?, ?)""", txnId, l.account(), l.direction(), l.amountPaise(), l.customerId(),
                    l.returnItemId());
        }
    }

    public List<Map<String, Object>> entriesForReturn(UUID returnId) {
        return jdbc.queryForList("""
                SELECT t.id AS transaction_id, t.txn_type, t.posted_at, e.account_code, e.direction, e.amount_paise
                FROM ledger_transactions t JOIN ledger_entries e ON e.transaction_id = t.id
                WHERE t.return_id = ? ORDER BY t.id, e.id""", returnId);
    }

    /** Store credit balance = credits - debits on STORE_CREDIT_LIABILITY for the customer. */
    public long storeCreditBalance(long customerId) {
        Long v = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE direction WHEN 'CREDIT' THEN amount_paise ELSE -amount_paise END), 0)
                FROM ledger_entries WHERE account_code = 'STORE_CREDIT_LIABILITY' AND customer_id = ?""",
                Long.class, customerId);
        return v == null ? 0 : v;
    }

    // ----------------------------------------------------------------------------------------------------
    // Reconciliation (Part 1 §7.5)
    // ----------------------------------------------------------------------------------------------------
    public record Check(String check, String description, long mismatches, List<String> sampleIds) {}

    public List<Check> reconcile(int staleMinutes) {
        List<Check> out = new ArrayList<>();
        out.add(check("R1", "Every SUCCEEDED attempt has exactly one matching ledger posting (and vice versa)", """
                SELECT a.id::text FROM refund_attempts a JOIN refunds f ON f.id = a.refund_id
                LEFT JOIN ledger_transactions t ON t.refund_attempt_id = a.id
                LEFT JOIN LATERAL (SELECT SUM(amount_paise) s FROM ledger_entries e WHERE e.transaction_id = t.id
                   AND e.account_code IN ('GATEWAY_SETTLEMENT','STORE_CREDIT_LIABILITY') AND e.direction = 'CREDIT') x ON true
                WHERE a.status = 'SUCCEEDED' AND (t.id IS NULL OR x.s IS DISTINCT FROM f.amount_paise)
                UNION ALL
                SELECT t.id::text FROM ledger_transactions t LEFT JOIN refund_attempts a ON a.id = t.refund_attempt_id
                WHERE t.txn_type <> 'REVERSAL' AND (a.id IS NULL OR a.status <> 'SUCCEEDED')"""));
        out.add(check("R2", "Net sales returns per REFUNDED return equal its approved refund", """
                SELECT r.id::text FROM returns r
                LEFT JOIN LATERAL (
                  SELECT SUM(CASE WHEN e.account_code = 'SALES_RETURNS' AND e.direction = 'DEBIT' THEN e.amount_paise
                                  WHEN e.account_code = 'SALES_RETURNS' AND e.direction = 'CREDIT' THEN -e.amount_paise
                                  WHEN e.account_code = 'RETURN_CONDITION_RECOVERY' AND e.direction = 'CREDIT' THEN -e.amount_paise
                                  WHEN e.account_code = 'RETURN_CONDITION_RECOVERY' AND e.direction = 'DEBIT' THEN e.amount_paise
                                  ELSE 0 END) net
                  FROM ledger_transactions t JOIN ledger_entries e ON e.transaction_id = t.id WHERE t.return_id = r.id) l ON true
                WHERE r.status = 'REFUNDED' AND l.net IS DISTINCT FROM r.approved_refund_paise"""));
        out.add(check("R4", "Ledger is globally balanced", """
                SELECT 'global' WHERE (SELECT COALESCE(SUM(CASE direction WHEN 'DEBIT' THEN amount_paise
                  ELSE -amount_paise END), 0) FROM ledger_entries) <> 0"""));
        out.add(check("R5", "No refund attempt stuck PENDING/UNKNOWN for more than " + staleMinutes + " minutes",
                "SELECT id::text FROM refund_attempts WHERE status IN ('PENDING','UNKNOWN') AND created_at < now() - "
                        + "make_interval(mins => " + staleMinutes + ")"));
        return out;
    }

    private Check check(String name, String description, String sql) {
        List<String> ids = jdbc.queryForList(sql + " LIMIT 1000", String.class);
        return new Check(name, description, ids.size(), ids.stream().limit(5).toList());
    }

    public Map<String, Long> accountBalances() {
        Map<String, Long> out = new LinkedHashMap<>();
        jdbc.query("""
                SELECT account_code, SUM(CASE direction WHEN 'DEBIT' THEN amount_paise ELSE -amount_paise END)
                FROM ledger_entries GROUP BY account_code ORDER BY account_code""",
                rs -> {
                    out.put(rs.getString(1), rs.getLong(2));
                });
        return out;
    }
}
