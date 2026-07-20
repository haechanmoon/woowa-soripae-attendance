package com.woowasoripae.attendance.web.attendance.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Officer marking a member's in-person roll call result.
 * result must be PRESENT, LATE, or ABSENT (PENDING/REJECTED are not valid for a face-to-face check).
 * lateMinutes is required (and only meaningful) when result == LATE; >= 60 is rejected client-side by
 * the officer UI but the server still auto-converts to ABSENT per policy if it slips through.
 */
public record FaceCheckRequest(
        @NotNull Long memberId,
        @NotNull LocalDate practiceDate,
        @NotNull LocalTime scheduledStartTime,
        @NotNull LocalTime scheduledEndTime,
        @NotNull FaceCheckResult result,
        @Min(0) Integer lateMinutes
) {
    public enum FaceCheckResult {
        PRESENT, LATE, ABSENT
    }
}
