package com.woowasoripae.attendance.web.schedule.dto;

import com.woowasoripae.attendance.domain.schedule.ScheduleChangeLog;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 임원 관리: 마감 후 이번 주 스케줄을 바꾼 내역. 승인 대상이 아니라 열람용이다. */
public record ScheduleChangeLogResponse(
        Long id,
        Long memberId,
        String memberName,
        String part,
        LocalDate practiceDate,
        LocalTime previousStartTime,
        LocalTime newStartTime,
        ScheduleChangeLog.Kind kind,
        String reason,
        LocalDateTime changedAt
) {
    public static ScheduleChangeLogResponse from(ScheduleChangeLog log) {
        return new ScheduleChangeLogResponse(
                log.getId(),
                log.getMember().getId(),
                log.getMember().getName(),
                log.getMember().getPart(),
                log.getPracticeDate(),
                log.getPreviousStartTime(),
                log.getNewStartTime(),
                log.kind(),
                log.getReason(),
                log.getCreatedAt());
    }
}
