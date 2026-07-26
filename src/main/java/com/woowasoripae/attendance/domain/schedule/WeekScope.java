package com.woowasoripae.attendance.domain.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** 주간 현황에서 볼 주. 주는 항상 월요일에 시작한다. */
public enum WeekScope {

    /** 오늘이 속한 주. "이번 주에 누가 언제 오지?" */
    THIS {
        @Override
        public LocalDate weekStart(LocalDate today) {
            return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
    },
    /** 스케줄 등록이 향하는 주. ScheduleService.register와 같은 기준(오늘이 월요일이어도 다음 주). */
    NEXT {
        @Override
        public LocalDate weekStart(LocalDate today) {
            return today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        }
    };

    public abstract LocalDate weekStart(LocalDate today);
}
