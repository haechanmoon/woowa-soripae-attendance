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
import com.woowasoripae.attendance.web.schedule.dto.ThisWeekChangeRequest;
import com.woowasoripae.attendance.web.schedule.dto.ScheduleResponse;
import com.woowasoripae.attendance.web.schedule.dto.WeekRegistrationResponse;
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
    @Mock
    private ScheduleChangeLogRepository scheduleChangeLogRepository;

    private ScheduleService scheduleService;

    private Member member;
    private static final LocalTime START = LocalTime.of(13, 0);

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleService(practiceScheduleRepository, memberRepository, songMemberRepository,
                scheduleChangeLogRepository);
        member = new Member("김유미", null, "세션");
        ReflectionTestUtils.setField(member, "id", 1L);
    }

    /** register()가 저장하려 한 PracticeSchedule을 가로채 실제 계산된 날짜를 확인한다. */
    private LocalDate captureRegisteredDate(DayOfWeek dayOfWeek) {
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(practiceScheduleRepository.findByMemberIdAndPracticeDateOrderByStartTimeAsc(any(), any()))
                .willReturn(List.of());
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
            given(practiceScheduleRepository.findByMemberIdAndPracticeDateOrderByStartTimeAsc(any(), any()))
                    .willReturn(List.of());
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
        @DisplayName("시작 시각만 저장한다 (합주를 몇 시간 하든 출석은 하루 한 번이라 종료 시각은 쓰지 않는다)")
        void storesStartTimeOnly() {
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(practiceScheduleRepository.findByMemberIdAndPracticeDateOrderByStartTimeAsc(any(), any()))
                    .willReturn(List.of());
            given(practiceScheduleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            ScheduleResponse response = scheduleService.register(
                    1L, new ScheduleRegisterRequest(DayOfWeek.MONDAY, LocalTime.of(19, 30)));

            assertThat(response.startTime()).isEqualTo(LocalTime.of(19, 30));
        }

        @Test
        @DisplayName("같은 날짜에 이미 등록했으면 시각이 달라도 409를 던진다 (하루 한 타임)")
        void throwsConflictWhenAlreadyRegisteredThatDay() {
            LocalDate alreadyRegistered = LocalDate.now().plusDays(3);
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(practiceScheduleRepository.findByMemberIdAndPracticeDateOrderByStartTimeAsc(any(), any()))
                    .willReturn(List.of(new PracticeSchedule(member, alreadyRegistered, LocalTime.of(13, 0))));

            // 13시가 이미 있는 날에 19시를 추가로 등록하려는 상황
            assertThatThrownBy(() -> scheduleService.register(
                    1L, new ScheduleRegisterRequest(DayOfWeek.MONDAY, LocalTime.of(19, 0))))
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
            assertThat(response.memberCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("한 주에 여러 번 오는 사람도 주간 인원에서는 한 명으로만 센다")
        void weeklyMemberCountIsDistinctAcrossDays() {
            LocalDate monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
            Member yumi = memberOf(1L, "김유미", "세션");
            Member hyebin = memberOf(2L, "최혜빈", "보컬");
            givenSchedules(
                    new PracticeSchedule(yumi, monday, LocalTime.of(13, 0)),
                    new PracticeSchedule(yumi, monday.plusDays(2), LocalTime.of(13, 0)),
                    new PracticeSchedule(yumi, monday.plusDays(4), LocalTime.of(19, 0)),
                    new PracticeSchedule(hyebin, monday.plusDays(2), LocalTime.of(13, 0))
            );

            WeeklyScheduleResponse response = scheduleService.getWeeklySchedule(WeekScope.NEXT);

            // 주 3회 오는 김유미 + 1회 오는 최혜빈 = 2명. 요일별 합계(4)와 달라야 한다.
            assertThat(response.memberCount()).isEqualTo(2);
            assertThat(response.days().stream().mapToInt(WeeklyScheduleResponse.DaySchedule::memberCount).sum())
                    .isEqualTo(4);
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

    /**
     * 등록 마감이 일요일이라, 주 초의 "다음 주 미등록 전원"은 이상 신호가 아니다.
     * 반대로 이번 주 미등록은 이제 되돌릴 수 없는 확정된 결과다. 두 주가 성격이 달라 각각 볼 수 있어야 한다.
     */
    @Nested
    @DisplayName("getRegistrationStatus - 주별 스케줄 등록 현황")
    class RegistrationStatus {

        private Member memberOf(long id, String name, String part) {
            Member m = new Member(name, null, part);
            ReflectionTestUtils.setField(m, "id", id);
            return m;
        }

        /** 곡에 배정된 부원만 등록 대상이므로, 배정 목록과 등록된 스케줄을 함께 깔아준다. */
        private void givenAssigned(List<Member> assigned, PracticeSchedule... registered) {
            given(memberRepository.findAll()).willReturn(assigned);
            given(songMemberRepository.findDistinctMemberIds())
                    .willReturn(assigned.stream().map(Member::getId).toList());
            given(practiceScheduleRepository.findByPracticeDateBetween(any(), any()))
                    .willReturn(List.of(registered));
        }

        /** 서비스가 실제로 어느 주를 조회했는지 저장소 호출 인자에서 뽑아낸다. */
        private LocalDate captureWeekStart() {
            ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
            ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
            verify(practiceScheduleRepository).findByPracticeDateBetween(from.capture(), to.capture());
            assertThat(to.getValue()).isEqualTo(from.getValue().plusDays(6));
            return from.getValue();
        }

        private List<String> namesOf(List<WeekRegistrationResponse.MemberBrief> briefs) {
            return briefs.stream().map(WeekRegistrationResponse.MemberBrief::name).toList();
        }

        @Test
        @DisplayName("등록한 사람과 안 한 사람을 갈라 담고, 둘을 합치면 곡 배정 인원이 된다")
        void splitsAssignedMembersByRegistration() {
            Member yumi = memberOf(1L, "김유미", "세션");
            Member hyebin = memberOf(2L, "최혜빈", "보컬");
            Member junho = memberOf(3L, "박준호", "세션");
            givenAssigned(List.of(yumi, hyebin, junho),
                    new PracticeSchedule(yumi, LocalDate.now(), START));

            WeekRegistrationResponse response = scheduleService.getRegistrationStatus(WeekScope.THIS);

            assertThat(namesOf(response.registered())).containsExactly("김유미");
            assertThat(namesOf(response.notRegistered())).containsExactly("최혜빈", "박준호");
            assertThat(response.registered().size() + response.notRegistered().size()).isEqualTo(3);
        }

        @Test
        @DisplayName("곡에 배정되지 않은 부원은 등록할 이유가 없으므로 어느 쪽에도 세지 않는다")
        void excludesMembersWithoutSongAssignment() {
            Member yumi = memberOf(1L, "김유미", "세션");
            Member newbie = memberOf(9L, "신입", "보컬");
            given(memberRepository.findAll()).willReturn(List.of(yumi, newbie));
            given(songMemberRepository.findDistinctMemberIds()).willReturn(List.of(1L));
            given(practiceScheduleRepository.findByPracticeDateBetween(any(), any())).willReturn(List.of());

            WeekRegistrationResponse response = scheduleService.getRegistrationStatus(WeekScope.THIS);

            assertThat(namesOf(response.notRegistered())).containsExactly("김유미");
            assertThat(response.registered()).isEmpty();
        }

        @Test
        @DisplayName("한 주에 여러 날 등록한 사람도 등록 인원에서는 한 명이다")
        void countsEachMemberOnce() {
            Member yumi = memberOf(1L, "김유미", "세션");
            LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            givenAssigned(List.of(yumi),
                    new PracticeSchedule(yumi, monday, START),
                    new PracticeSchedule(yumi, monday.plusDays(2), START));

            WeekRegistrationResponse response = scheduleService.getRegistrationStatus(WeekScope.THIS);

            assertThat(response.registered()).hasSize(1);
            assertThat(response.notRegistered()).isEmpty();
        }

        @ParameterizedTest
        @EnumSource(WeekScope.class)
        @DisplayName("요청한 주를 그대로 조회하고, 어느 주를 봤는지 응답에 실어 보낸다")
        void looksUpRequestedWeekAndEchoesIt(WeekScope scope) {
            givenAssigned(List.of());

            WeekRegistrationResponse response = scheduleService.getRegistrationStatus(scope);

            assertThat(response.scope()).isEqualTo(scope);
            assertThat(captureWeekStart()).isEqualTo(scope.weekStart(LocalDate.now()));
            assertThat(response.weekEnd()).isEqualTo(response.weekStart().plusDays(6));
        }

        @Test
        @DisplayName("주를 지정하지 않으면 오늘 요일에 맞는 기본 주를 골라 조회한다")
        void picksDefaultWeekWhenScopeOmitted() {
            givenAssigned(List.of());

            WeekRegistrationResponse response = scheduleService.getRegistrationStatus(null);

            WeekScope expected = WeekScope.defaultForRegistration(LocalDate.now());
            assertThat(response.scope()).isEqualTo(expected);
            assertThat(captureWeekStart()).isEqualTo(expected.weekStart(LocalDate.now()));
        }

        @Test
        @DisplayName("다음 주는 아직 마감 전이라 남은 날을 알려준다")
        void tellsRemainingDaysForNextWeek() {
            givenAssigned(List.of());

            WeekRegistrationResponse response = scheduleService.getRegistrationStatus(WeekScope.NEXT);

            assertThat(response.daysUntilDeadline()).isBetween(0, 6);
        }

        @Test
        @DisplayName("이번 주는 이미 마감이 지나 되돌릴 수 없으므로 남은 날이 없다")
        void hasNoDeadlineForThisWeek() {
            givenAssigned(List.of());

            WeekRegistrationResponse response = scheduleService.getRegistrationStatus(WeekScope.THIS);

            assertThat(response.daysUntilDeadline()).isNull();
        }

        @Test
        @DisplayName("이번 주는 손쓸 수 없으니 독려할 대상이 아니다")
        void thisWeekIsNeverUrgent() {
            givenAssigned(List.of());

            assertThat(scheduleService.getRegistrationStatus(WeekScope.THIS).urgent()).isFalse();
        }

        @Test
        @DisplayName("다음 주가 급한지 여부는 마감까지 남은 날 하나로 정해진다 (화면이 임의로 기준을 정하지 않는다)")
        void urgencyFollowsRemainingDays() {
            givenAssigned(List.of());

            WeekRegistrationResponse response = scheduleService.getRegistrationStatus(WeekScope.NEXT);

            assertThat(response.urgent()).isEqualTo(response.daysUntilDeadline() <= WeekScope.URGENT_DAYS);
            // 기본 탭이 다음 주로 넘어가는 날부터 빨갛게 보여야 설명이 하나로 이어진다.
            assertThat(response.urgent())
                    .isEqualTo(WeekScope.defaultForRegistration(LocalDate.now()) == WeekScope.NEXT);
        }
    }

    /**
     * 마감(일요일)이 지난 뒤 이번 주를 바꾸는 길. 승인 게이트 없이 바로 반영하되 사유를 남긴다.
     * 막아버리면 "못 가게 됐어요"가 조용한 회피가 되고, 승인을 기다리게 하면 합주를 다녀와도 인증을 못 한다.
     * 대신 기록이 남아 임원이 사후에 보고 판단한다.
     */
    @Nested
    @DisplayName("changeThisWeek - 마감 후 이번 주 변경")
    class ChangeThisWeek {

        private final LocalDate today = LocalDate.now();
        private final LocalTime NEW_TIME = LocalTime.of(19, 0);

        private ThisWeekChangeRequest change(LocalDate date, LocalTime startTime) {
            return new ThisWeekChangeRequest(date, startTime, "까먹고 못 올렸어요");
        }

        private void givenScheduleOn(LocalDate date, LocalTime startTime) {
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(practiceScheduleRepository.findByMemberIdAndPracticeDateOrderByStartTimeAsc(1L, date))
                    .willReturn(startTime == null ? List.of() : List.of(new PracticeSchedule(member, date, startTime)));
        }

        private ScheduleChangeLog captureLog() {
            ArgumentCaptor<ScheduleChangeLog> captor = ArgumentCaptor.forClass(ScheduleChangeLog.class);
            verify(scheduleChangeLogRepository).save(captor.capture());
            return captor.getValue();
        }

        @Test
        @DisplayName("등록이 없던 날에 시각을 주면 새로 만들고, 기록에는 이전 시각이 비어 있다")
        void addsScheduleAndLogsAsNew() {
            givenScheduleOn(today, null);
            given(practiceScheduleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            scheduleService.changeThisWeek(1L, change(today, NEW_TIME));

            ArgumentCaptor<PracticeSchedule> saved = ArgumentCaptor.forClass(PracticeSchedule.class);
            verify(practiceScheduleRepository).save(saved.capture());
            assertThat(saved.getValue().getStartTime()).isEqualTo(NEW_TIME);
            assertThat(saved.getValue().getPracticeDate()).isEqualTo(today);

            ScheduleChangeLog log = captureLog();
            assertThat(log.getPreviousStartTime()).isNull();
            assertThat(log.getNewStartTime()).isEqualTo(NEW_TIME);
        }

        @Test
        @DisplayName("이미 등록한 날에 다른 시각을 주면 그 자리를 바꾸고, 기록에 이전과 이후가 모두 남는다")
        void movesScheduleAndLogsBothTimes() {
            givenScheduleOn(today, START);
            given(practiceScheduleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            scheduleService.changeThisWeek(1L, change(today, NEW_TIME));

            ScheduleChangeLog log = captureLog();
            assertThat(log.getPreviousStartTime()).isEqualTo(START);
            assertThat(log.getNewStartTime()).isEqualTo(NEW_TIME);
            // 하루 한 타임이므로 새로 만들지 않고 기존 것을 옮긴다.
            verify(practiceScheduleRepository, never()).delete(any());
        }

        @Test
        @DisplayName("시각을 비우면 그날 등록을 지우고, 기록에는 이후 시각이 비어 있다")
        void cancelsScheduleAndLogsAsRemoved() {
            givenScheduleOn(today, START);

            scheduleService.changeThisWeek(1L, change(today, null));

            verify(practiceScheduleRepository).delete(any());
            ScheduleChangeLog log = captureLog();
            assertThat(log.getPreviousStartTime()).isEqualTo(START);
            assertThat(log.getNewStartTime()).isNull();
        }

        @Test
        @DisplayName("지울 등록이 없는데 취소하면 404를 던진다")
        void throwsNotFoundWhenCancelingNothing() {
            givenScheduleOn(today, null);

            assertThatThrownBy(() -> scheduleService.changeThisWeek(1L, change(today, null)))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.NOT_FOUND);

            verify(scheduleChangeLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("이미 지난 날은 바꿀 수 없다 (지나간 합주를 뒤늦게 만들어낼 수 없다)")
        void rejectsPastDates() {
            assertThatThrownBy(() -> scheduleService.changeThisWeek(1L, change(today.minusDays(1), NEW_TIME)))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);

            verify(practiceScheduleRepository, never()).save(any());
            verify(scheduleChangeLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("오늘은 바꿀 수 있다 (아직 합주 전일 수 있다)")
        void allowsToday() {
            givenScheduleOn(today, null);
            given(practiceScheduleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            scheduleService.changeThisWeek(1L, change(today, NEW_TIME));

            verify(practiceScheduleRepository).save(any());
        }

        @Test
        @DisplayName("이번 주를 넘어선 날은 이 통로로 바꿀 수 없다 (다음 주는 평소 등록으로)")
        void rejectsDatesBeyondThisWeek() {
            LocalDate nextWeek = WeekScope.NEXT.weekStart(today);

            assertThatThrownBy(() -> scheduleService.changeThisWeek(1L, change(nextWeek, NEW_TIME)))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("사유는 손대지 않고 그대로 기록에 남는다 (임원이 판단할 근거가 이것뿐이다)")
        void keepsReasonVerbatim() {
            givenScheduleOn(today, null);
            given(practiceScheduleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            scheduleService.changeThisWeek(1L, new ThisWeekChangeRequest(today, NEW_TIME, "알바 시간이 바뀌었어요"));

            assertThat(captureLog().getReason()).isEqualTo("알바 시간이 바뀌었어요");
            assertThat(captureLog().getMember()).isEqualTo(member);
        }
    }

    @Nested
    @DisplayName("getThisWeekChanges - 임원이 보는 이번 주 변경 내역")
    class ThisWeekChanges {

        @Test
        @DisplayName("이번 주(월~일) 범위로 조회한다")
        void looksUpThisWeek() {
            given(scheduleChangeLogRepository.findWithMemberByPracticeDateBetween(any(), any()))
                    .willReturn(List.of());

            scheduleService.getThisWeekChanges();

            ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
            ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
            verify(scheduleChangeLogRepository).findWithMemberByPracticeDateBetween(from.capture(), to.capture());

            assertThat(from.getValue()).isEqualTo(WeekScope.THIS.weekStart(LocalDate.now()));
            assertThat(to.getValue()).isEqualTo(from.getValue().plusDays(6));
        }

        @Test
        @DisplayName("무엇이 바뀐 것인지(등록/이동/취소)는 남은 시각으로 판별된다")
        void classifiesChangeByStoredTimes() {
            LocalDate date = LocalDate.now();

            assertThat(new ScheduleChangeLog(member, date, null, START, "사유").kind())
                    .isEqualTo(ScheduleChangeLog.Kind.ADDED);
            assertThat(new ScheduleChangeLog(member, date, START, LocalTime.of(19, 0), "사유").kind())
                    .isEqualTo(ScheduleChangeLog.Kind.MOVED);
            assertThat(new ScheduleChangeLog(member, date, START, null, "사유").kind())
                    .isEqualTo(ScheduleChangeLog.Kind.CANCELED);
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
