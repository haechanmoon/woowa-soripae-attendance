package com.woowasoripae.attendance.domain.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

    /** 마감까지 이만큼 이하로 남으면 독려해야 할 때다. 기본 탭도, 미등록자를 빨갛게 칠하는 기준도 이 값 하나를 쓴다. */
    public static final int URGENT_DAYS = 2;

    public abstract LocalDate weekStart(LocalDate today);

    /** 다음 주 스케줄 등록 마감. 이번 주 일요일 자정이므로, 마감일은 이번 주의 마지막 날이다. */
    public static LocalDate registrationDeadline(LocalDate today) {
        return today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    }

    /** 마감까지 남은 날. 일요일이면 0. */
    public static int daysUntilDeadline(LocalDate today) {
        return (int) ChronoUnit.DAYS.between(today, registrationDeadline(today));
    }

    /**
     * 등록 현황을 열었을 때 먼저 볼 주.
     * 주 초에 다음 주가 전원 미등록인 건 마감 전이라 당연한 일이라, 그때는 이미 확정된 이번 주 결과를 보여준다.
     * 마감이 급해지는 금요일부터는 아직 독려할 수 있는 다음 주로 넘어간다.
     */
    public static WeekScope defaultForRegistration(LocalDate today) {
        return daysUntilDeadline(today) <= URGENT_DAYS ? NEXT : THIS;
    }
}
