package com.returnright.model.dto.response;

import com.returnright.model.enums.CustomerTimelineStep;
import com.returnright.model.enums.TimelineEntryState;
import java.time.Instant;

/**
 * One step on the customer timeline.
 *
 * @param at when the step was reached; {@code null} for upcoming steps
 */
public record TimelineEntryResponse(
    CustomerTimelineStep step, TimelineEntryState state, Instant at) {}
