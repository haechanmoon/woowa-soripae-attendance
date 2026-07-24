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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
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
