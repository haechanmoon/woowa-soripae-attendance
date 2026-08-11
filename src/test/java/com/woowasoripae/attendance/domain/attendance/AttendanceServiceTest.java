package com.woowasoripae.attendance.domain.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.woowasoripae.attendance.domain.member.Member;
import com.woowasoripae.attendance.domain.member.MemberRepository;
import com.woowasoripae.attendance.domain.schedule.PracticeSchedule;
import com.woowasoripae.attendance.domain.schedule.PracticeScheduleRepository;
import com.woowasoripae.attendance.global.exception.ApiException;
import com.woowasoripae.attendance.global.file.FileStorageService;
import com.woowasoripae.attendance.web.attendance.dto.ApproveAttendanceRequest;
import com.woowasoripae.attendance.web.attendance.dto.AttendanceRecordResponse;
import com.woowasoripae.attendance.web.attendance.dto.FaceCheckRequest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * AttendanceService는 리포지토리/외부 서비스에 의존하므로 실제 DB 대신 Mockito 목(mock)으로 대체한다.
 * "이 상황(given)에서 이 메서드를 호출하면(when) 이런 결과/부수효과가 나온다(then)" 형태로 케이스를 나눈다.
 *
 * Member는 JPA @GeneratedValue라서 생성자로 id를 못 넣는다. 영속화된 것처럼 흉내내려고
 * ReflectionTestUtils로 id 필드를 직접 채워넣는다 - 실무에서 흔히 쓰는 테스트 전용 우회법이다.
 */
@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private PracticeScheduleRepository practiceScheduleRepository;
    @Mock
    private FileStorageService fileStorageService;

    private AttendancePolicyProperties policy;
    private FineCalculator fineCalculator;
    private AttendanceService attendanceService;

    private Member member;
    /** 코어타임. 그날 등록이 없는 부원의 지각 판정 기준이 된다. */
    private static final LocalTime CORE_START = LocalTime.of(19, 0);
    /** 부원이 직접 등록한 시각. 등록이 있으면 이쪽이 판정 기준이 된다. */
    private static final LocalTime REGISTERED_START = LocalTime.of(13, 0);

    @BeforeEach
    void setUp() {
        // FineCalculator는 목이 아니라 실제 객체를 사용한다: 계산 로직 자체는 이미 FineCalculatorTest가
        // 검증하므로, 여기서는 "AttendanceService가 결과를 올바르게 반영하는지"만 보면 된다.
        policy = new AttendancePolicyProperties(CORE_START, LocalTime.of(21, 0), 100, 6000, 60);
        fineCalculator = new FineCalculator(policy);
        attendanceService = new AttendanceService(
                attendanceRecordRepository, memberRepository, practiceScheduleRepository,
                fineCalculator, policy, fileStorageService);

        member = new Member("최시원", null, "보컬");
        ReflectionTestUtils.setField(member, "id", 1L);
    }

    @Nested
    @DisplayName("submitPhoto - 하루 한 번 인증")
    class SubmitPhoto {

        private final MockMultipartFile photo =
                new MockMultipartFile("photo", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});

        private void givenNoRecordToday() {
            given(attendanceRecordRepository.findByMemberIdAndPracticeDate(1L, LocalDate.now()))
                    .willReturn(Optional.empty());
        }

        /** 저장하려 한 기록을 가로채 어떤 시각을 기준으로 판정했는지 확인한다. */
        private LocalTime captureScheduledStartTime() {
            ArgumentCaptor<AttendanceRecord> captor = ArgumentCaptor.forClass(AttendanceRecord.class);
            verify(attendanceRecordRepository).save(captor.capture());
            return captor.getValue().getScheduledStartTime();
        }

        @Test
        @DisplayName("그날 등록해둔 스케줄의 시작 시각을 판정 기준으로 삼는다")
        void usesRegisteredStartTimeAsBaseline() {
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            givenNoRecordToday();
            given(practiceScheduleRepository.findByMemberIdAndPracticeDateOrderByStartTimeAsc(1L, LocalDate.now()))
                    .willReturn(List.of(new PracticeSchedule(member, LocalDate.now(), REGISTERED_START)));
            given(fileStorageService.store(photo)).willReturn("https://files/test.jpg");
            given(attendanceRecordRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

            AttendanceRecordResponse response = attendanceService.submitPhoto(1L, photo);

            assertThat(response.status()).isEqualTo(AttendanceStatus.PENDING);
            assertThat(response.method()).isEqualTo(AttendanceMethod.PHOTO);
            assertThat(captureScheduledStartTime()).isEqualTo(REGISTERED_START);
        }

        @Test
        @DisplayName("그날 등록한 스케줄이 없으면 코어타임을 판정 기준으로 삼는다")
        void fallsBackToCoreTimeWhenNotRegistered() {
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            givenNoRecordToday();
            given(practiceScheduleRepository.findByMemberIdAndPracticeDateOrderByStartTimeAsc(1L, LocalDate.now()))
                    .willReturn(List.of());
            given(fileStorageService.store(photo)).willReturn("https://files/test.jpg");
            given(attendanceRecordRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

            attendanceService.submitPhoto(1L, photo);

            assertThat(captureScheduledStartTime()).isEqualTo(CORE_START);
        }

        @Test
        @DisplayName("오늘 이미 인증했으면 409를 던지고 사진도 저장하지 않는다")
        void throwsConflictWhenAlreadyCertifiedToday() {
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(attendanceRecordRepository.findByMemberIdAndPracticeDate(1L, LocalDate.now()))
                    .willReturn(Optional.of(AttendanceRecord.createPendingPhotoSubmission(
                            member, LocalDate.now(), REGISTERED_START, "old.jpg", java.time.LocalDateTime.now())));

            assertThatThrownBy(() -> attendanceService.submitPhoto(1L, photo))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);

            verify(fileStorageService, never()).store(any());
            verify(attendanceRecordRepository, never()).save(any());
        }

        @Test
        @DisplayName("존재하지 않는 회원이면 404 not found를 던진다")
        void throwsNotFoundWhenMemberMissing() {
            given(memberRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> attendanceService.submitPhoto(99L, photo))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
        }
    }

    /**
     * "오늘 오기로 해놓고 인증을 안 올린 사람"을 임원이 판단할 수 있게 모아준다.
     * 자동으로 결석 처리하지 않는다 - 그냥 까먹은 것일 수 있어 사람이 봐야 한다.
     */
    @Nested
    @DisplayName("getUncertifiedMembers - 오늘 미인증자")
    class UncertifiedMembers {

        private final LocalDate today = LocalDate.now();

        private Member memberOf(long id, String name) {
            Member m = new Member(name, null, "세션");
            ReflectionTestUtils.setField(m, "id", id);
            return m;
        }

        private AttendanceRecord recordOf(Member m, AttendanceStatus status) {
            AttendanceRecord record = AttendanceRecord.createPendingPhotoSubmission(
                    m, today, REGISTERED_START, "p.jpg", java.time.LocalDateTime.now());
            ReflectionTestUtils.setField(record, "status", status);
            return record;
        }

        @Test
        @DisplayName("오늘 등록했지만 출석 기록이 없는 사람만, 예정 시각과 함께 돌려준다")
        void returnsRegisteredMembersWithoutRecord() {
            Member yumi = memberOf(1L, "김유미");
            Member hyebin = memberOf(2L, "최혜빈");
            given(practiceScheduleRepository.findWithMemberByPracticeDateBetween(today, today))
                    .willReturn(List.of(
                            new PracticeSchedule(yumi, today, LocalTime.of(13, 0)),
                            new PracticeSchedule(hyebin, today, LocalTime.of(19, 0))));
            given(attendanceRecordRepository.findByPracticeDate(today))
                    .willReturn(List.of(recordOf(hyebin, AttendanceStatus.PRESENT)));

            var uncertified = attendanceService.getUncertifiedMembers(today);

            assertThat(uncertified).hasSize(1);
            assertThat(uncertified.get(0).name()).isEqualTo("김유미");
            assertThat(uncertified.get(0).scheduledStartTime()).isEqualTo(LocalTime.of(13, 0));
        }

        @Test
        @DisplayName("반려된 기록만 있는 사람은 아직 인증하지 않은 것으로 본다")
        void rejectedRecordCountsAsUncertified() {
            Member yumi = memberOf(1L, "김유미");
            given(practiceScheduleRepository.findWithMemberByPracticeDateBetween(today, today))
                    .willReturn(List.of(new PracticeSchedule(yumi, today, LocalTime.of(13, 0))));
            given(attendanceRecordRepository.findByPracticeDate(today))
                    .willReturn(List.of(recordOf(yumi, AttendanceStatus.REJECTED)));

            assertThat(attendanceService.getUncertifiedMembers(today))
                    .extracting(r -> r.name())
                    .containsExactly("김유미");
        }

        @Test
        @DisplayName("심사중(PENDING)이면 이미 올린 것이므로 미인증이 아니다")
        void pendingRecordIsNotUncertified() {
            Member yumi = memberOf(1L, "김유미");
            given(practiceScheduleRepository.findWithMemberByPracticeDateBetween(today, today))
                    .willReturn(List.of(new PracticeSchedule(yumi, today, LocalTime.of(13, 0))));
            given(attendanceRecordRepository.findByPracticeDate(today))
                    .willReturn(List.of(recordOf(yumi, AttendanceStatus.PENDING)));

            assertThat(attendanceService.getUncertifiedMembers(today)).isEmpty();
        }

        @Test
        @DisplayName("오늘 등록 자체가 없으면 아무도 나오지 않는다 (등록 안 한 사람은 대상이 아니다)")
        void unregisteredMembersAreNotListed() {
            given(practiceScheduleRepository.findWithMemberByPracticeDateBetween(today, today))
                    .willReturn(List.of());
            given(attendanceRecordRepository.findByPracticeDate(today)).willReturn(List.of());

            assertThat(attendanceService.getUncertifiedMembers(today)).isEmpty();
        }
    }

    /**
     * "날짜를 옮겨야만 알 수 있던" 놓친 인증을, 넘기지 않고도 지난 2주치를 한 번에 모아 보여준다.
     */
    @Nested
    @DisplayName("getMissedAttendanceSummary - 지난 2주 놓친 인증")
    class MissedAttendanceSummary {

        private final LocalDate today = LocalDate.now();
        private final LocalDate yesterday = today.minusDays(1);
        private final LocalDate twoDaysAgo = today.minusDays(2);

        private Member memberOf(long id, String name) {
            Member m = new Member(name, null, "세션");
            ReflectionTestUtils.setField(m, "id", id);
            return m;
        }

        private AttendanceRecord recordOf(Member m, LocalDate date, AttendanceStatus status) {
            AttendanceRecord record = AttendanceRecord.createPendingPhotoSubmission(
                    m, date, REGISTERED_START, "p.jpg", java.time.LocalDateTime.now());
            ReflectionTestUtils.setField(record, "status", status);
            return record;
        }

        @Test
        @DisplayName("등록만 해두고 인증도 대면 체크도 안 된 지난 날짜를 날짜별로 묶어 돌려준다")
        void groupsUncertifiedSchedulesByDate() {
            Member yumi = memberOf(1L, "김유미");
            Member hyebin = memberOf(2L, "최혜빈");
            LocalDate from = yesterday.minusDays(13);
            given(practiceScheduleRepository.findWithMemberByPracticeDateBetween(from, yesterday))
                    .willReturn(List.of(
                            new PracticeSchedule(yumi, twoDaysAgo, LocalTime.of(13, 0)),
                            new PracticeSchedule(hyebin, yesterday, LocalTime.of(19, 0))));
            given(attendanceRecordRepository.findByPracticeDateBetween(from, yesterday))
                    .willReturn(List.of());

            var summary = attendanceService.getMissedAttendanceSummary();

            assertThat(summary).hasSize(2);
            assertThat(summary.get(0).practiceDate()).isEqualTo(twoDaysAgo);
            assertThat(summary.get(0).members()).extracting(r -> r.name()).containsExactly("김유미");
            assertThat(summary.get(1).practiceDate()).isEqualTo(yesterday);
            assertThat(summary.get(1).members()).extracting(r -> r.name()).containsExactly("최혜빈");
        }

        @Test
        @DisplayName("이미 인증/대면 체크가 끝난 날짜는 목록에서 빠진다")
        void excludesAlreadyCertifiedDates() {
            Member yumi = memberOf(1L, "김유미");
            LocalDate from = yesterday.minusDays(13);
            given(practiceScheduleRepository.findWithMemberByPracticeDateBetween(from, yesterday))
                    .willReturn(List.of(new PracticeSchedule(yumi, yesterday, LocalTime.of(13, 0))));
            given(attendanceRecordRepository.findByPracticeDateBetween(from, yesterday))
                    .willReturn(List.of(recordOf(yumi, yesterday, AttendanceStatus.PRESENT)));

            assertThat(attendanceService.getMissedAttendanceSummary()).isEmpty();
        }

        @Test
        @DisplayName("반려된 기록만 있는 날짜는 여전히 놓친 것으로 본다")
        void rejectedRecordStillCountsAsMissed() {
            Member yumi = memberOf(1L, "김유미");
            LocalDate from = yesterday.minusDays(13);
            given(practiceScheduleRepository.findWithMemberByPracticeDateBetween(from, yesterday))
                    .willReturn(List.of(new PracticeSchedule(yumi, yesterday, LocalTime.of(13, 0))));
            given(attendanceRecordRepository.findByPracticeDateBetween(from, yesterday))
                    .willReturn(List.of(recordOf(yumi, yesterday, AttendanceStatus.REJECTED)));

            assertThat(attendanceService.getMissedAttendanceSummary())
                    .extracting(r -> r.practiceDate())
                    .containsExactly(yesterday);
        }

        @Test
        @DisplayName("오늘 날짜는 아직 연습이 끝나지 않았을 수 있어 대상에서 제외한다")
        void excludesToday() {
            given(practiceScheduleRepository.findWithMemberByPracticeDateBetween(any(), any()))
                    .willAnswer(invocation -> {
                        LocalDate to = invocation.getArgument(1);
                        assertThat(to).isEqualTo(yesterday);
                        return List.of();
                    });
            given(attendanceRecordRepository.findByPracticeDateBetween(any(), any())).willReturn(List.of());

            assertThat(attendanceService.getMissedAttendanceSummary()).isEmpty();
        }
    }

    @Nested
    @DisplayName("approve")
    class Approve {

        @Test
        @DisplayName("PENDING 사진 기록을 지각 시간에 따라 LATE로 승인하고 벌금을 계산한다")
        void approvesPendingRecordAsLate() {
            AttendanceRecord pending = AttendanceRecord.createPendingPhotoSubmission(
                    member, LocalDate.now(), REGISTERED_START, "photo.jpg", java.time.LocalDateTime.now());
            given(attendanceRecordRepository.findById(10L)).willReturn(Optional.of(pending));

            AttendanceRecordResponse response = attendanceService.approve(10L, new ApproveAttendanceRequest(15));

            assertThat(response.status()).isEqualTo(AttendanceStatus.LATE);
            assertThat(response.lateMinutes()).isEqualTo(15);
            assertThat(response.fineAmount()).isEqualTo(1500);
        }

        @Test
        @DisplayName("이미 승인/반려된 기록은 다시 승인할 수 없다")
        void throwsConflictWhenRecordNotPending() {
            AttendanceRecord decided = AttendanceRecord.createPendingPhotoSubmission(
                    member, LocalDate.now(), REGISTERED_START, "photo.jpg", java.time.LocalDateTime.now());
            decided.reject(java.time.LocalDateTime.now());
            given(attendanceRecordRepository.findById(10L)).willReturn(Optional.of(decided));

            assertThatThrownBy(() -> attendanceService.approve(10L, new ApproveAttendanceRequest(0)))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("잘못 처리한 기록을 삭제해 미등록 상태로 되돌린다")
        void deletesRecord() {
            AttendanceRecord record = AttendanceRecord.createPendingPhotoSubmission(
                    member, LocalDate.now(), REGISTERED_START, "photo.jpg", java.time.LocalDateTime.now());
            given(attendanceRecordRepository.findById(10L)).willReturn(Optional.of(record));

            attendanceService.delete(10L);

            verify(attendanceRecordRepository).delete(record);
        }

        @Test
        @DisplayName("존재하지 않는 기록이면 404를 던지고 아무것도 지우지 않는다")
        void throwsNotFoundWhenRecordMissing() {
            given(attendanceRecordRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> attendanceService.delete(999L))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);

            verify(attendanceRecordRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("faceCheck")
    class FaceCheck {

        @Test
        @DisplayName("결과가 LATE인데 lateMinutes가 없으면 400 bad request를 던진다")
        void throwsBadRequestWhenLateWithoutMinutes() {
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            FaceCheckRequest request = new FaceCheckRequest(
                    1L, LocalDate.now(), FaceCheckRequest.FaceCheckResult.LATE, null);

            assertThatThrownBy(() -> attendanceService.faceCheck(request))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("결과가 ABSENT면 지각 시간과 무관하게 정액 벌금이 부과된다")
        void absentResultAppliesFlatFine() {
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(attendanceRecordRepository.findByMemberIdAndPracticeDate(1L, LocalDate.now()))
                    .willReturn(Optional.empty());
            given(practiceScheduleRepository.findByMemberIdAndPracticeDateOrderByStartTimeAsc(1L, LocalDate.now()))
                    .willReturn(List.of());
            given(attendanceRecordRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

            FaceCheckRequest request = new FaceCheckRequest(
                    1L, LocalDate.now(), FaceCheckRequest.FaceCheckResult.ABSENT, null);

            AttendanceRecordResponse response = attendanceService.faceCheck(request);

            assertThat(response.status()).isEqualTo(AttendanceStatus.ABSENT);
            assertThat(response.fineAmount()).isEqualTo(6000);
            assertThat(response.method()).isEqualTo(AttendanceMethod.FACE_TO_FACE);
        }
    }
}
