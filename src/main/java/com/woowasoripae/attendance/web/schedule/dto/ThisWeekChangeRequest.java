package com.woowasoripae.attendance.web.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 마감이 지난 뒤 이번 주 스케줄을 바꾼다.
 * 요일이 아니라 날짜를 받는 이유: 이번 주는 이미 절반이 지나 있을 수 있어, 어느 날인지가 요일보다 분명하다.
 */
public record ThisWeekChangeRequest(
        @NotNull LocalDate practiceDate,
        /** 비우면 그날 등록을 취소한다는 뜻. 등록/시간 변경/취소를 한 가지 요청으로 표현한다. */
        LocalTime startTime,
        @NotBlank(message = "사유를 적어주세요.") @Size(max = 200) String reason
) {
}
