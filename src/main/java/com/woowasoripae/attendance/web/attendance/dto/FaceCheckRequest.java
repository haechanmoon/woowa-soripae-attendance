package com.woowasoripae.attendance.web.attendance.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Officer marking a member's in-person roll call result.
 * 지각 판정 기준 시각은 서버가 그날 등록 스케줄(없으면 코어타임)에서 정하므로 요청에 담지 않는다.
 * result must be PRESENT, LATE, or ABSENT (PENDING/REJECTED are not valid for a face-to-face check).
 * lateMinutes is required (and only meaningful) when result == LATE; >= 60 is rejected client-side by
 * the officer UI but the server still auto-converts to ABSENT per policy if it slips through.
 */
public record FaceCheckRequest(
        @NotNull Long memberId,
        @NotNull LocalDate practiceDate,
        @NotNull FaceCheckResult result,
        @Min(0) Integer lateMinutes
) {
    public enum FaceCheckResult {
        PRESENT, LATE, ABSENT
    }
}
