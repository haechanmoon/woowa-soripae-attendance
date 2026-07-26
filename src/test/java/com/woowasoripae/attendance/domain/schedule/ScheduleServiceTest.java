package com.woowasoripae.attendance.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.woowasoripae.attendance.domain.member.Member;
import com.woowasoripae.attendance.domain.member.MemberRepository;
import com.woowasoripae.attendance.domain.song.SongMemberRepository;
import com.woowasoripae.attendance.global.exception.ApiException;
import com.woowasoripae.attendance.web.schedule.dto.ScheduleRegisterRequest;
import com.woowasoripae.attendance.web.schedule.dto.ScheduleResponse;
import com.woowasoripae.attendance.web.schedule.dto.WeeklyScheduleResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * "다음 주 스케줄 등록"의 날짜 계산이 이 서비스의 핵심이다.
 * 구현을 그대로 베껴 기대값을 만들면 의미가 없으므로, 날짜의 성질(요일이 일치하는가,
 * 항상 오늘보다 미래인가, 7개 요일이 연속된 한 주를 이루는가)로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private PracticeScheduleRepository practiceScheduleRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private SongMemberRepository songMemberRepository;

    private ScheduleService scheduleService;

    private Member member;
    private static final LocalTime START = LocalTime.of(13, 0);

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleService(practiceScheduleRepository, memberRepository, songMemberRepository);
        member = new Member("김유미", null, "세션");
        ReflectionTestUtils.setField(member, "id", 1L);
    }

    /** register()가 저장하려 한 PracticeSchedule을 가로채 실제 계산된 날짜를 확인한다. */
    private LocalDate captureRegisteredDate(DayOfWeek dayOfWeek) {
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(practiceScheduleRepository.findByMemberIdAndPracticeDateAndStartTime(any(), any(), any()))
                .willReturn(Optional.empty());
        given(practiceScheduleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        scheduleService.register(1L, new ScheduleRegisterRequest(dayOfWeek, START));

        ArgumentCaptor<PracticeSchedule> captor = ArgumentCaptor.forClass(PracticeSchedule.class);
        verify(practiceScheduleRepository).save(captor.capture());
        return captor.getValue().getPracticeDate();
    }

    @Nested
    @DisplayName("register - 다음 주 날짜 계산")
    class NextWeekResolution {

        @ParameterizedTest(name = "{0} 등록 시 해당 요일의 미래 날짜가 잡힌다")
        @EnumSource(DayOfWeek.class)
        @DisplayName("요청한 요일과 실제 저장되는 날짜의 요일이 항상 일치한다")
        void resolvedDateMatchesRequestedDayOfWeek(DayOfWeek dayOfWeek) {
            LocalDate resolved = captureRegisteredDate(dayOfWeek);

            assertThat(resolved.getDayOfWeek()).isEqualTo(dayOfWeek);
            // 오늘과 같은 요일을 골라도 "다음 주"여야 하므로 반드시 미래다.
            assertThat(resolved).isAfter(LocalDate.now());
        }

        @Test
        @DisplayName("월~일 7개 요일이 월요일로 시작하는 연속된 한 주를 이룬다")
        void sevenDaysFormOneConsecutiveWeek() {
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(practiceScheduleRepository.findByMemberIdAndPracticeDateAndStartTime(any(), any(), any()))
                    .willReturn(Optional.empty());
            given(practiceScheduleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            Arrays.stream(DayOfWeek.values())
                    .forEach(day -> scheduleService.register(1L, new ScheduleRegisterRequest(day, START)));

            ArgumentCaptor<PracticeSchedule> captor = ArgumentCaptor.forClass(PracticeSchedule.class);
            verify(practiceScheduleRepository, times(7)).save(captor.capture());
            List<LocalDate> resolved = captor.getAllValues().stream()
                    .map(PracticeSchedule::getPracticeDate)
                    .toList();

            assertThat(resolved.get(0).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
            for (int i = 1; i < resolved.size(); i++) {
                assertThat(resolved.get(i)).isEqualTo(resolved.get(0).plusDays(i));
            }
        }
    }

    @Nested
    @DisplayName("register - 검증")
    class RegisterValidation {

        @Test
        @DisplayName("종료 시각은 시작 시각의 2시간 뒤로 고정된다")
        void endTimeIsAlwaysTwoHoursAfterStart() {
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(practiceScheduleRepository.findByMemberIdAndPracticeDateAndStartTime(any(), any(), any()))
                    .willReturn(Optional.empty());
            given(practiceScheduleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            ScheduleResponse response = scheduleService.register(
                    1L, new ScheduleRegisterRequest(DayOfWeek.MONDAY, LocalTime.of(19, 30)));

            assertThat(response.startTime()).isEqualTo(LocalTime.of(19, 30));
            assertThat(response.endTime()).isEqualTo(LocalTime.of(21, 30));
        }

        @Test
        @DisplayName("같은 날짜/시각으로 이미 등록했으면 409를 던진다")
        void throwsConflictOnDuplicate() {
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(practiceScheduleRepository.findByMemberIdAndPracticeDateAndStartTime(any(), any(), any()))
                    .willReturn(Optional.of(new PracticeSchedule(member, LocalDate.now().plusDays(3), START)));

            assertThatThrownBy(() -> scheduleService.register(
                    1L, new ScheduleRegisterRequest(DayOfWeek.MONDAY, START)))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);

            verify(practiceScheduleRepository, never()).save(any());
        }

        @Test
        @DisplayName("존재하지 않는 부원이면 404를 던진다")
        void throwsNotFoundForUnknownMember() {
            given(memberRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> scheduleService.register(
                    99L, new ScheduleRegisterRequest(DayOfWeek.MONDAY, START)))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    /**
     * "요일별로 누가 오는지"를 한 화면에서 보기 위한 주간 집계.
     * 집계 규칙(7일이 빠짐없이 나오는가, 같은 시각끼리 묶이는가, 인원수를 어떻게 세는가)이 핵심이므로
     * 저장소가 돌려준 원본 데이터와 집계 결과의 관계로 검증한다.
     */
    @Nested
    @DisplayName("getWeeklySchedule - 요일별 출석 현황")
    class WeeklyView {

        private Member memberOf(long id, String name, String part) {
            Member m = new Member(name, null, part);
            ReflectionTestUtils.setField(m, "id", id);
            return m;
        }

        private void givenSchedules(PracticeSchedule... schedules) {
            given(practiceScheduleRepository.findWithMemberByPracticeDateBetween(any(), any()))
                    .willReturn(List.of(schedules));
        }

        /** 서비스가 실제로 어느 주를 조회했는지 저장소 호출 인자에서 뽑아낸다. */
        private LocalDate captureWeekStart(WeekScope scope) {
            givenSchedules();
            scheduleService.getWeeklySchedule(scope);

            ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
            ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
            verify(practiceScheduleRepository).findWithMemberByPracticeDateBetween(from.capture(), to.capture());
            assertThat(to.getValue()).isEqualTo(from.getValue().plusDays(6));
            return from.getValue();
        }

        @Test
        @DisplayName("등록이 하나도 없는 요일까지 포함해 월~일 7일이 순서대로 나온다")
        void alwaysReturnsSevenDaysInOrder() {
            givenSchedules();

            WeeklyScheduleResponse response = scheduleService.getWeeklySchedule(WeekScope.NEXT);

            assertThat(response.days()).hasSize(7);
            assertThat(response.days()).extracting(WeeklyScheduleResponse.DaySchedule::dayOfWeek)
                    .containsExactly(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
            assertThat(response.days()).allSatisfy(day -> {
                assertThat(day.slots()).isEmpty();
                assertThat(day.memberCount()).isZero();
            });
            assertThat(response.weekEnd()).isEqualTo(response.weekStart().plusDays(6));
        }

        @Test
        @DisplayName("같은 요일의 같은 시작 시각은 한 시간대로 묶이고, 시간대는 이른 순으로 정렬된다")
        void groupsSameStartTimeAndSortsByTime() {
            LocalDate monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
            Member yumi = memberOf(1L, "김유미", "세션");
            Member hyebin = memberOf(2L, "최혜빈", "보컬");
            Member junho = memberOf(3L, "박준호", "세션");
            givenSchedules(
                    new PracticeSchedule(junho, monday, LocalTime.of(19, 0)),
                    new PracticeSchedule(yumi, monday, LocalTime.of(13, 0)),
                    new PracticeSchedule(hyebin, monday, LocalTime.of(13, 0))
            );

            WeeklyScheduleResponse response = scheduleService.getWeeklySchedule(WeekScope.NEXT);
            var mondaySlots = dayOf(response, DayOfWeek.MONDAY).slots();

            assertThat(mondaySlots).extracting(WeeklyScheduleResponse.TimeSlot::startTime)
                    .containsExactly(LocalTime.of(13, 0), LocalTime.of(19, 0));
            assertThat(mondaySlots.get(0).attendees()).extracting(WeeklyScheduleResponse.Attendee::name)
                    .containsExactly("김유미", "최혜빈"); // 표시가 흔들리지 않도록 이름순
            assertThat(mondaySlots.get(0).endTime()).isEqualTo(LocalTime.of(15, 0));
            assertThat(mondaySlots.get(1).attendees()).extracting(WeeklyScheduleResponse.Attendee::name)
                    .containsExactly("박준호");
        }

        @Test
        @DisplayName("하루에 두 타임을 등록한 사람은 요일 인원수에서 한 명으로만 센다")
        void countsEachMemberOncePerDay() {
            LocalDate monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
            Member yumi = memberOf(1L, "김유미", "세션");
            Member hyebin = memberOf(2L, "최혜빈", "보컬");
            givenSchedules(
                    new PracticeSchedule(yumi, monday, LocalTime.of(13, 0)),
                    new PracticeSchedule(yumi, monday, LocalTime.of(19, 0)),
                    new PracticeSchedule(hyebin, monday, LocalTime.of(19, 0))
            );

            WeeklyScheduleResponse response = scheduleService.getWeeklySchedule(WeekScope.NEXT);

            assertThat(dayOf(response, DayOfWeek.MONDAY).memberCount()).isEqualTo(2);
            assertThat(dayOf(response, DayOfWeek.MONDAY).slots()).hasSize(2);
        }

        @Test
        @DisplayName("서로 다른 요일의 등록은 각자의 요일에만 담긴다")
        void placesSchedulesUnderTheirOwnDay() {
            LocalDate monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
            givenSchedules(
                    new PracticeSchedule(memberOf(1L, "김유미", "세션"), monday, LocalTime.of(13, 0)),
                    new PracticeSchedule(memberOf(2L, "최혜빈", "보컬"), monday.plusDays(3), LocalTime.of(13, 0))
            );

            WeeklyScheduleResponse response = scheduleService.getWeeklySchedule(WeekScope.NEXT);

            assertThat(dayOf(response, DayOfWeek.MONDAY).memberCount()).isEqualTo(1);
            assertThat(dayOf(response, DayOfWeek.THURSDAY).memberCount()).isEqualTo(1);
            assertThat(response.days()).filteredOn(d -> d.memberCount() > 0).hasSize(2);
            assertThat(response.totalCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("NEXT는 스케줄 등록이 향하는 주와 같은 주를 조회한다")
        void nextScopeMatchesRegistrationWeek() {
            LocalDate weekStart = captureWeekStart(WeekScope.NEXT);

            assertThat(weekStart.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(weekStart).isAfter(LocalDate.now());
        }

        @Test
        @DisplayName("THIS는 오늘이 속한 주(월~일)를 조회한다")
        void thisScopeCoversToday() {
            LocalDate weekStart = captureWeekStart(WeekScope.THIS);

            assertThat(weekStart.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(LocalDate.now()).isBetween(weekStart, weekStart.plusDays(6));
        }

        private WeeklyScheduleResponse.DaySchedule dayOf(WeeklyScheduleResponse response, DayOfWeek dayOfWeek) {
            return response.days().stream().filter(d -> d.dayOfWeek() == dayOfWeek).findFirst().orElseThrow();
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("본인 스케줄이면 삭제한다")
        void deletesOwnSchedule() {
            PracticeSchedule schedule = new PracticeSchedule(member, LocalDate.now().plusDays(3), START);
            given(practiceScheduleRepository.findById(5L)).willReturn(Optional.of(schedule));

            scheduleService.delete(1L, 5L);

            verify(practiceScheduleRepository).delete(schedule);
        }

        @Test
        @DisplayName("남의 스케줄은 삭제할 수 없다")
        void cannotDeleteOthersSchedule() {
            Member other = new Member("최혜빈", null, "세션");
            ReflectionTestUtils.setField(other, "id", 2L);
            PracticeSchedule schedule = new PracticeSchedule(other, LocalDate.now().plusDays(3), START);
            given(practiceScheduleRepository.findById(5L)).willReturn(Optional.of(schedule));

            assertThatThrownBy(() -> scheduleService.delete(1L, 5L))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);

            verify(practiceScheduleRepository, never()).delete(any());
        }

        @Test
        @DisplayName("존재하지 않는 스케줄이면 404를 던진다")
        void throwsNotFoundForUnknownSchedule() {
            given(practiceScheduleRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> scheduleService.delete(1L, 999L))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
