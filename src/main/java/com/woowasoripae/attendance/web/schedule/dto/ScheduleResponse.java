package com.woowasoripae.attendance.web.schedule.dto;

import com.woowasoripae.attendance.domain.schedule.PracticeSchedule;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

public record ScheduleResponse(
        Long id,
        Long memberId,
        String memberName,
        LocalDate practiceDate,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime
) {
    public static ScheduleResponse from(PracticeSchedule schedule) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getMember().getId(),
                schedule.getMember().getName(),
                schedule.getPracticeDate(),
                schedule.getPracticeDate().getDayOfWeek(),
                schedule.getStartTime(),
                schedule.getEndTime()
        );
    }
}
