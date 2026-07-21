package com.woowasoripae.attendance.domain.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.woowasoripae.attendance.domain.member.Member;
import com.woowasoripae.attendance.domain.member.MemberRepository;
import com.woowasoripae.attendance.global.exception.ApiException;
import com.woowasoripae.attendance.global.file.FileStorageService;
import com.woowasoripae.attendance.web.attendance.dto.ApproveAttendanceRequest;
import com.woowasoripae.attendance.web.attendance.dto.AttendanceRecordResponse;
import com.woowasoripae.attendance.web.attendance.dto.FaceCheckRequest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private FileStorageService fileStorageService;

    private FineCalculator fineCalculator;
    private AttendanceService attendanceService;

    private Member member;
    private static final LocalTime SCHEDULED_START = LocalTime.of(19, 0);

    @BeforeEach
    void setUp() {
        // FineCalculator는 목이 아니라 실제 객체를 사용한다: 계산 로직 자체는 이미 FineCalculatorTest가
        // 검증하므로, 여기서는 "AttendanceService가 결과를 올바르게 반영하는지"만 보면 된다.
        fineCalculator = new FineCalculator(new AttendancePolicyProperties(
                LocalTime.of(19, 0), LocalTime.of(21, 0), 100, 6000, 60));
        attendanceService = new AttendanceService(
                attendanceRecordRepository, memberRepository, fineCalculator, fileStorageService);

        member = new Member("최시원", null, "보컬");
        ReflectionTestUtils.setField(member, "id", 1L);
    }

    @Nested
    @DisplayName("submitPhoto")
    class SubmitPhoto {

        @Test
        @DisplayName("동일 스케줄에 대한 기존 제출이 없으면 사진을 저장하고 PENDING 기록을 생성한다")
        void createsRecordWhenNoExistingSubmission() {
            MockMultipartFile photo = new MockMultipartFile("photo", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(attendanceRecordRepository.findByMemberIdAndPracticeDateAndScheduledStartTime(
                    1L, LocalDate.now(), SCHEDULED_START)).willReturn(Optional.empty());
            given(fileStorageService.store(photo)).willReturn("https://files/test.jpg");
            given(attendanceRecordRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

            AttendanceRecordResponse response = attendanceService.submitPhoto(1L, SCHEDULED_START, photo);

            assertThat(response.status()).isEqualTo(AttendanceStatus.PENDING);
            assertThat(response.method()).isEqualTo(AttendanceMethod.PHOTO);
            assertThat(response.photoUrl()).isEqualTo("https://files/test.jpg");
        }

        @Test
        @DisplayName("동일 스케줄로 이미 제출한 기록이 있으면 409 conflict를 던진다")
        void throwsConflictWhenAlreadySubmitted() {
            MockMultipartFile photo = new MockMultipartFile("photo", "face.jpg", "image/jpeg", new byte[]{1});
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(attendanceRecordRepository.findByMemberIdAndPracticeDateAndScheduledStartTime(
                    1L, LocalDate.now(), SCHEDULED_START)).willReturn(Optional.of(
                    AttendanceRecord.createPendingPhotoSubmission(
                            member, LocalDate.now(), SCHEDULED_START, SCHEDULED_START.plusHours(2),
                            "old.jpg", java.time.LocalDateTime.now())));

            assertThatThrownBy(() -> attendanceService.submitPhoto(1L, SCHEDULED_START, photo))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);

            // 실패 케이스에서는 새 사진이 저장되면 안 된다 - 부수효과가 안 일어났는지도 검증 대상이다.
            verify(fileStorageService, never()).store(any());
        }

        @Test
        @DisplayName("존재하지 않는 회원이면 404 not found를 던진다")
        void throwsNotFoundWhenMemberMissing() {
            MockMultipartFile photo = new MockMultipartFile("photo", "face.jpg", "image/jpeg", new byte[]{1});
            given(memberRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> attendanceService.submitPhoto(99L, SCHEDULED_START, photo))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("approve")
    class Approve {

        @Test
        @DisplayName("PENDING 사진 기록을 지각 시간에 따라 LATE로 승인하고 벌금을 계산한다")
        void approvesPendingRecordAsLate() {
            AttendanceRecord pending = AttendanceRecord.createPendingPhotoSubmission(
                    member, LocalDate.now(), SCHEDULED_START, SCHEDULED_START.plusHours(2),
                    "photo.jpg", java.time.LocalDateTime.now());
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
                    member, LocalDate.now(), SCHEDULED_START, SCHEDULED_START.plusHours(2),
                    "photo.jpg", java.time.LocalDateTime.now());
            decided.reject(java.time.LocalDateTime.now());
            given(attendanceRecordRepository.findById(10L)).willReturn(Optional.of(decided));

            assertThatThrownBy(() -> attendanceService.approve(10L, new ApproveAttendanceRequest(0)))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
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
                    1L, LocalDate.now(), SCHEDULED_START, SCHEDULED_START.plusHours(2),
                    FaceCheckRequest.FaceCheckResult.LATE, null);

            assertThatThrownBy(() -> attendanceService.faceCheck(request))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("결과가 ABSENT면 지각 시간과 무관하게 정액 벌금이 부과된다")
        void absentResultAppliesFlatFine() {
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(attendanceRecordRepository.findByMemberIdAndPracticeDateAndScheduledStartTime(
                    1L, LocalDate.now(), SCHEDULED_START)).willReturn(Optional.empty());
            given(attendanceRecordRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

            FaceCheckRequest request = new FaceCheckRequest(
                    1L, LocalDate.now(), SCHEDULED_START, SCHEDULED_START.plusHours(2),
                    FaceCheckRequest.FaceCheckResult.ABSENT, null);

            AttendanceRecordResponse response = attendanceService.faceCheck(request);

            assertThat(response.status()).isEqualTo(AttendanceStatus.ABSENT);
            assertThat(response.fineAmount()).isEqualTo(6000);
            assertThat(response.method()).isEqualTo(AttendanceMethod.FACE_TO_FACE);
        }
    }
}
