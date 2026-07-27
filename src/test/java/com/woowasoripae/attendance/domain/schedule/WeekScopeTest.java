package com.woowasoripae.attendance.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 등록 마감이 일요일이라, 주 초에는 "다음 주 미등록"이 당연히 전원이다.
 * 그 사실을 화면이 스스로 설명하도록 기본으로 볼 주와 마감까지 남은 날을 여기서 계산한다.
 * 구현을 베끼면 검증이 되지 않으므로, 요일을 하나씩 넣어 성질(마감 전인가, 오늘이 속한 주인가)로 확인한다.
 */
class WeekScopeTest {

    /** 요일별로 검사하려면 "그 요일인 어떤 날"이 필요하다. 특정 날짜에 의존하지 않도록 오늘 기준으로 만든다. */
    private LocalDate someDayOf(DayOfWeek dayOfWeek) {
        return LocalDate.now().with(TemporalAdjusters.nextOrSame(dayOfWeek));
    }

    private LocalDate everyDayOfWeek(int index) {
        return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusDays(index);
    }

    @Nested
    @DisplayName("weekStart")
    class WeekStart {

        @ParameterizedTest
        @EnumSource(DayOfWeek.class)
        @DisplayName("어느 요일에 봐도 주는 월요일에 시작한다")
        void alwaysStartsOnMonday(DayOfWeek today) {
            LocalDate day = someDayOf(today);

            assertThat(WeekScope.THIS.weekStart(day).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(WeekScope.NEXT.weekStart(day).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        }

        @ParameterizedTest
        @EnumSource(DayOfWeek.class)
        @DisplayName("THIS는 오늘을 품고, NEXT는 그 바로 다음 주다")
        void thisCoversTodayAndNextFollowsIt(DayOfWeek today) {
            LocalDate day = someDayOf(today);

            LocalDate thisStart = WeekScope.THIS.weekStart(day);
            assertThat(day).isBetween(thisStart, thisStart.plusDays(6));
            assertThat(WeekScope.NEXT.weekStart(day)).isEqualTo(thisStart.plusWeeks(1));
        }
    }

    @Nested
    @DisplayName("registrationDeadline - 다음 주 등록 마감")
    class Deadline {

        @ParameterizedTest
        @EnumSource(DayOfWeek.class)
        @DisplayName("마감은 이번 주의 마지막 날인 일요일이다")
        void isSundayOfThisWeek(DayOfWeek today) {
            LocalDate day = someDayOf(today);

            LocalDate deadline = WeekScope.registrationDeadline(day);

            assertThat(deadline.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
            assertThat(deadline).isEqualTo(WeekScope.THIS.weekStart(day).plusDays(6));
        }

        @ParameterizedTest
        @EnumSource(DayOfWeek.class)
        @DisplayName("마감은 아직 지나지 않았고, 아무리 멀어도 6일 뒤다")
        void isNeverPastAndAtMostSixDaysAway(DayOfWeek today) {
            LocalDate day = someDayOf(today);

            long remaining = ChronoUnit.DAYS.between(day, WeekScope.registrationDeadline(day));

            assertThat(remaining).isBetween(0L, 6L);
        }

        @Test
        @DisplayName("하루가 지날 때마다 마감까지 남은 날이 정확히 하루씩 줄어든다 (일요일에 0)")
        void countsDownOneDayAtATime() {
            long[] remaining = IntStream.range(0, 7)
                    .mapToLong(i -> ChronoUnit.DAYS.between(everyDayOfWeek(i), WeekScope.registrationDeadline(everyDayOfWeek(i))))
                    .toArray();

            assertThat(remaining).containsExactly(6, 5, 4, 3, 2, 1, 0);
        }
    }

    @Nested
    @DisplayName("defaultForRegistration - 화면을 열었을 때 먼저 볼 주")
    class DefaultScope {

        @Test
        @DisplayName("월~목은 이번 주를, 금~일은 다음 주를 먼저 보여준다")
        void switchesToNextWeekOnFriday() {
            WeekScope[] byDay = IntStream.range(0, 7)
                    .mapToObj(i -> WeekScope.defaultForRegistration(everyDayOfWeek(i)))
                    .toArray(WeekScope[]::new);

            assertThat(byDay).containsExactly(
                    WeekScope.THIS, WeekScope.THIS, WeekScope.THIS, WeekScope.THIS,
                    WeekScope.NEXT, WeekScope.NEXT, WeekScope.NEXT);
        }

        @ParameterizedTest
        @EnumSource(DayOfWeek.class)
        @DisplayName("기본이 다음 주로 바뀌는 시점과 마감이 급해지는 시점(2일 이하)이 어긋나지 않는다")
        void switchPointMatchesUrgency(DayOfWeek today) {
            LocalDate day = someDayOf(today);

            long remaining = ChronoUnit.DAYS.between(day, WeekScope.registrationDeadline(day));
            boolean urgent = remaining <= WeekScope.URGENT_DAYS;

            // 탭이 다음 주로 넘어가는 날부터 미등록자가 빨갛게 보여야 설명이 하나로 이어진다.
            assertThat(WeekScope.defaultForRegistration(day) == WeekScope.NEXT).isEqualTo(urgent);
        }

        @ParameterizedTest
        @EnumSource(DayOfWeek.class)
        @DisplayName("기본으로 고른 주는 언제나 실제로 존재하는 주다")
        void alwaysResolvesToARealWeek(DayOfWeek today) {
            LocalDate day = someDayOf(today);

            LocalDate start = WeekScope.defaultForRegistration(day).weekStart(day);

            assertThat(start.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(start).isAfterOrEqualTo(WeekScope.THIS.weekStart(day));
        }
    }
}
