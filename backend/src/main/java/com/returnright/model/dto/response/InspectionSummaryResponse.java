package com.returnright.model.dto.response;

import java.time.LocalDate;

/**
 * The "Today: 27 inspected" counter.
 *
 * @param date today in Asia/Kolkata
 * @param inspectedToday returns inspected or rejected after inspection today, by anyone
 * @param inspectedByYouToday the part of that done by the signed-in agent
 */
public record InspectionSummaryResponse(
    LocalDate date, long inspectedToday, long inspectedByYouToday) {}
