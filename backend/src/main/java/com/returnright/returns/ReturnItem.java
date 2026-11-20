package com.returnright.returns;

import com.returnright.core.enums.Decision;
import com.returnright.core.enums.Grade;
import com.returnright.core.enums.ItemStatus;
import com.returnright.core.enums.RejectReason;
import com.returnright.core.enums.ReturnReason;
import com.returnright.orders.OrderItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One physical unit being returned [A-05]. Status is written only by {@link ReturnStateMachine}. */
@Entity
@Table(name = "return_items")
public class ReturnItem {

    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_id")
    private ReturnRequest ret;
    @Column(name = "order_item_id", insertable = false, updatable = false)
    private Long orderItemId;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;
    private Short unitIndex;
    @Enumerated(EnumType.STRING)
    private ItemStatus status;
    @Enumerated(EnumType.STRING)
    private ReturnReason reason;
    private String reasonNote;
    private long unitPricePaise;
    @JdbcTypeCode(SqlTypes.JSON)
    private String eligibilitySnapshot;
    @Enumerated(EnumType.STRING)
    private Grade grade;
    @Enumerated(EnumType.STRING)
    private Decision decision;
    @Enumerated(EnumType.STRING)
    private RejectReason rejectReason;
    private Long refundPaise;
    private String inspectionNotes;
    private Long inspectedBy;
    private Instant inspectedAt;

    protected ReturnItem() {}

    ReturnItem(ReturnRequest ret, OrderItem orderItem, int unitIndex, ReturnReason reason, String note,
               String eligibilitySnapshotJson) {
        this.id = UUID.randomUUID();
        this.ret = ret;
        this.orderItem = orderItem;
        this.orderItemId = orderItem.getId();
        this.unitIndex = (short) unitIndex;
        this.status = ItemStatus.REQUESTED;
        this.reason = reason;
        this.reasonNote = note;
        this.unitPricePaise = orderItem.getUnitPricePaise();
        this.eligibilitySnapshot = eligibilitySnapshotJson;
    }

    Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status.name());
        m.put("grade", grade == null ? null : grade.name());
        m.put("decision", decision == null ? null : decision.name());
        m.put("reject_reason", rejectReason == null ? null : rejectReason.name());
        m.put("refund_paise", refundPaise);
        return m;
    }

    // ---- package-private mutators: state machine only ----
    void setStatus(ItemStatus s) { this.status = s; }
    void inspect(Decision d, Grade g, RejectReason rr, Long refund, String notes, Long by, Instant at) {
        this.decision = d;
        this.grade = g;
        this.rejectReason = rr;
        this.refundPaise = refund;
        this.inspectionNotes = notes;
        this.inspectedBy = by;
        this.inspectedAt = at;
    }

    public UUID getId() { return id; }
    public ReturnRequest getRet() { return ret; }
    public Long getOrderItemId() { return orderItemId; }
    public OrderItem getOrderItem() { return orderItem; }
    public int getUnitIndex() { return unitIndex; }
    public ItemStatus getStatus() { return status; }
    public ReturnReason getReason() { return reason; }
    public String getReasonNote() { return reasonNote; }
    public long getUnitPricePaise() { return unitPricePaise; }
    public String getEligibilitySnapshot() { return eligibilitySnapshot; }
    public Grade getGrade() { return grade; }
    public Decision getDecision() { return decision; }
    public RejectReason getRejectReason() { return rejectReason; }
    public Long getRefundPaise() { return refundPaise; }
    public String getInspectionNotes() { return inspectionNotes; }
    public Instant getInspectedAt() { return inspectedAt; }
}
