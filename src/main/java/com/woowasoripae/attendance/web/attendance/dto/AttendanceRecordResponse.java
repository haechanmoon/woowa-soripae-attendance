package com.woowasoripae.attendance.web.attendance.dto;

import com.woowasoripae.attendance.domain.attendance.AttendanceMethod;
import com.woowasoripae.attendance.domain.attendance.AttendanceRecord;
import com.woowasoripae.attendance.domain.attendance.AttendanceStatus;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record AttendanceRecordResponse(
        Long id,
        Long memberId,
        String memberName,
        LocalDate practiceDate,
        LocalTime scheduledStartTime,
        LocalTime scheduledEndTime,
        AttendanceMethod method,
        AttendanceStatus status,
        int lateMinutes,
        int fineAmount,
        String photoUrl,
        LocalDateTime submittedAt,
        LocalDateTime decidedAt,
        /** Only populated for PENDING photo submissions: officer-facing hint, auto-derived from submittedAt vs. scheduledStartTime. */
        Integer suggestedLateMinutes
) {

    public static AttendanceRecordResponse from(AttendanceRecord record) {
        Integer suggested = null;
        if (record.getStatus() == AttendanceStatus.PENDING) {
            long diffMinutes = Duration.between(record.getScheduledStartTime(), record.getSubmittedAt().toLocalTime()).toMinutes();
            suggested = (int) Math.max(diffMinutes, 0);
        }

        return new AttendanceRecordResponse(
                record.getId(),
                record.getMember().getId(),
                record.getMember().getName(),
                record.getPracticeDate(),
                record.getScheduledStartTime(),
                record.getScheduledEndTime(),
                record.getMethod(),
                record.getStatus(),
                record.getLateMinutes(),
                record.getFineAmount(),
                record.getPhotoUrl(),
                record.getSubmittedAt(),
                record.getDecidedAt(),
                suggested
        );
    }
}
