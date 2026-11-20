package com.returnright.model.dto.response;

import com.returnright.model.enums.CustomerTimelineStep;
import com.returnright.model.enums.RefundMethod;
import java.time.Instant;
import java.util.List;

/**
 * Everything the customer's return page shows. Sections that don't apply yet are {@code null}.
 *
 * @param statusMessage one customer-facing line for the header, e.g. "Approved · label ready"
 * @param estimatedRefundPaise sum of line totals before inspection
 * @param label present while the parcel still has to be dropped off or is on its way
 * @param refund present from inspection onwards when something was accepted
 * @param rejection present when the return was rejected
 */
public record ReturnTrackingResponse(
    String rmaNumber,
    String orderNumber,
    CustomerTimelineStep status,
    String statusMessage,
    RefundMethod refundMethod,
    Instant createdAt,
    long estimatedRefundPaise,
    List<TimelineEntryResponse> timeline,
    List<TrackedItemResponse> items,
    ReturnLabelResponse label,
    RefundSummaryResponse refund,
    RejectionResponse rejection) {}
