package com.woowasoripae.attendance.web.schedule.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 한 주(월~일)에 요일별로 누가 오는지 보여주기 위한 응답.
 * 등록이 없는 요일도 비어 있는 채로 포함해 화면이 항상 7일을 그릴 수 있게 한다.
 */
public record WeeklyScheduleResponse(
        LocalDate weekStart,
        LocalDate weekEnd,
        /* 이 주에 한 번이라도 오는 사람 수. 여러 날 오는 사람이 많아 요일별 합계를 쓰면
           부원 총원보다 큰 값이 나와 "거의 전원 등록"으로 오해된다. 그래서 중복 없는 사람 수로 센다. */
        int memberCount,
        List<DaySchedule> days
) {
    /** memberCount는 같은 날 여러 타임을 등록한 사람을 한 명으로 센 인원수다. */
    public record DaySchedule(LocalDate date, DayOfWeek dayOfWeek, int memberCount, List<TimeSlot> slots) {}

    public record TimeSlot(LocalTime startTime, LocalTime endTime, List<Attendee> attendees) {}

    public record Attendee(Long memberId, String name, String part) {}
}
