package com.woowasoripae.attendance.web.attendance.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Officer-confirmed late minutes for a PHOTO submission; >= absentThresholdMinutes(60) auto-converts to ABSENT. */
public record ApproveAttendanceRequest(
        @NotNull @Min(0) Integer lateMinutes
) {
}
