package com.returnright.model.dto.response;

import com.returnright.model.enums.CustomerTimelineStep;
import java.time.Instant;

/** A step the return has reached on the customer-facing timeline, and when. */
public record TimelineEventResponse(CustomerTimelineStep step, Instant at) {}
