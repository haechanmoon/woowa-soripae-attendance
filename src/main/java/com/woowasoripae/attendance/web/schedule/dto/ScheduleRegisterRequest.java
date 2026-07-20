package com.woowasoripae.attendance.web.schedule.dto;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

/** dayOfWeek: 등록하고 싶은 요일(항상 다음 주 기준으로 계산됨). startTime: 시작 시각(종료는 +2시간 고정). */
public record ScheduleRegisterRequest(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull LocalTime startTime
) {
}
