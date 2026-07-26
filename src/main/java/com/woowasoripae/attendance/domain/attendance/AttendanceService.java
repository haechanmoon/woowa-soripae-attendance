package com.woowasoripae.attendance.domain.attendance;

import com.woowasoripae.attendance.domain.member.Member;
import com.woowasoripae.attendance.domain.member.MemberRepository;
import com.woowasoripae.attendance.domain.schedule.PracticeSchedule;
import com.woowasoripae.attendance.domain.schedule.PracticeScheduleRepository;
import com.woowasoripae.attendance.global.exception.ApiException;
import com.woowasoripae.attendance.global.file.FileStorageService;
import com.woowasoripae.attendance.web.attendance.dto.ApproveAttendanceRequest;
import com.woowasoripae.attendance.web.attendance.dto.AttendanceRecordResponse;
import com.woowasoripae.attendance.web.attendance.dto.FaceCheckRequest;
import com.woowasoripae.attendance.web.attendance.dto.UncertifiedMemberResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final MemberRepository memberRepository;
    private final PracticeScheduleRepository practiceScheduleRepository;
    private final FineCalculator fineCalculator;
    private final AttendancePolicyProperties policy;
    private final FileStorageService fileStorageService;

    public AttendanceService(
            AttendanceRecordRepository attendanceRecordRepository,
            MemberRepository memberRepository,
            PracticeScheduleRepository practiceScheduleRepository,
            FineCalculator fineCalculator,
            AttendancePolicyProperties policy,
            FileStorageService fileStorageService
    ) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.memberRepository = memberRepository;
        this.practiceScheduleRepository = practiceScheduleRepository;
        this.fineCalculator = fineCalculator;
        this.policy = policy;
        this.fileStorageService = fileStorageService;
    }

    /**
     * 지각을 몇 분으로 볼지 재는 기준 시각. 그날 본인이 등록해둔 스케줄의 시작 시각이고,
     * 등록이 없으면 코어타임을 기준으로 한다. 부원이 인증할 때 시간을 고르지 않아도 되도록 서버가 정한다.
     */
    private LocalTime resolveBaselineStartTime(Long memberId, LocalDate practiceDate) {
        return practiceScheduleRepository
                .findByMemberIdAndPracticeDateOrderByStartTimeAsc(memberId, practiceDate)
                .stream().findFirst()
                .map(PracticeSchedule::getStartTime)
                .orElse(policy.coreTimeStart());
    }

    /** 하루 한 번 인증하면 그날 출석은 끝난다. 곡을 몇 개 하든, 몇 시간을 하든 두 번 올릴 필요가 없다. */
    @Transactional
    public AttendanceRecordResponse submitPhoto(Long memberId, MultipartFile photo) {
        Member member = getMember(memberId);
        LocalDateTime submittedAt = LocalDateTime.now();
        LocalDate practiceDate = submittedAt.toLocalDate();

        attendanceRecordRepository.findByMemberIdAndPracticeDate(memberId, practiceDate)
                .ifPresent(existing -> {
                    throw ApiException.conflict("오늘은 이미 인증했어요. 하루 한 번이면 출석 인정됩니다.");
                });

        LocalTime baselineStartTime = resolveBaselineStartTime(memberId, practiceDate);
        String photoUrl = fileStorageService.store(photo);
        AttendanceRecord record = AttendanceRecord.createPendingPhotoSubmission(
                member, practiceDate, baselineStartTime, photoUrl, submittedAt);

        return AttendanceRecordResponse.from(attendanceRecordRepository.save(record));
    }

    @Transactional
    public AttendanceRecordResponse approve(Long recordId, ApproveAttendanceRequest request) {
        AttendanceRecord record = getPendingPhotoRecord(recordId);
        FineCalculator.Evaluation evaluation = fineCalculator.evaluateLateMinutes(request.lateMinutes());
        record.applyDecision(evaluation, LocalDateTime.now());
        return AttendanceRecordResponse.from(record);
    }

    @Transactional
    public AttendanceRecordResponse reject(Long recordId) {
        AttendanceRecord record = getPendingPhotoRecord(recordId);
        record.reject(LocalDateTime.now());
        return AttendanceRecordResponse.from(record);
    }

    /** 임원 관리 > 대면 출석 체크: 잘못 처리한 기록을 완전히 삭제해 미등록 상태로 되돌린다. */
    @Transactional
    public void delete(Long recordId) {
        AttendanceRecord record = attendanceRecordRepository.findById(recordId)
                .orElseThrow(() -> ApiException.notFound("존재하지 않는 출석 기록입니다. id=" + recordId));
        attendanceRecordRepository.delete(record);
    }

    @Transactional
    public AttendanceRecordResponse faceCheck(FaceCheckRequest request) {
        Member member = getMember(request.memberId());

        FineCalculator.Evaluation evaluation = switch (request.result()) {
            case PRESENT -> fineCalculator.evaluateLateMinutes(0);
            case LATE -> {
                if (request.lateMinutes() == null) {
                    throw ApiException.badRequest("지각 처리 시 lateMinutes는 필수입니다.");
                }
                yield fineCalculator.evaluateLateMinutes(request.lateMinutes());
            }
            case ABSENT -> fineCalculator.evaluateAbsent();
        };

        LocalDateTime now = LocalDateTime.now();
        AttendanceRecord record = attendanceRecordRepository
                .findByMemberIdAndPracticeDate(request.memberId(), request.practiceDate())
                .orElseGet(() -> AttendanceRecord.createFaceToFaceDecision(
                        member, request.practiceDate(),
                        resolveBaselineStartTime(request.memberId(), request.practiceDate()), evaluation, now));

        if (record.getId() != null) {
            record.applyDecision(evaluation, now);
        }

        return AttendanceRecordResponse.from(attendanceRecordRepository.save(record));
    }

    /**
     * 임원 관리: 오늘 오기로 등록해놓고 아직 인증하지 않은 부원.
     * 반려된 기록만 있는 사람은 아직 인증하지 않은 것으로 본다(다시 올려야 하므로).
     * 등록하지 않은 부원은 애초에 올 의무가 없어 대상이 아니다.
     */
    public List<UncertifiedMemberResponse> getUncertifiedMembers(LocalDate practiceDate) {
        Set<Long> certifiedMemberIds = attendanceRecordRepository.findByPracticeDate(practiceDate).stream()
                .filter(record -> record.getStatus() != AttendanceStatus.REJECTED)
                .map(record -> record.getMember().getId())
                .collect(Collectors.toSet());

        return practiceScheduleRepository.findWithMemberByPracticeDateBetween(practiceDate, practiceDate).stream()
                .filter(schedule -> !certifiedMemberIds.contains(schedule.getMember().getId()))
                .sorted(java.util.Comparator.comparing(PracticeSchedule::getStartTime))
                .map(schedule -> new UncertifiedMemberResponse(
                        schedule.getMember().getId(),
                        schedule.getMember().getName(),
                        schedule.getMember().getPart(),
                        schedule.getStartTime()))
                .toList();
    }

    public List<AttendanceRecordResponse> getPendingPhotoQueue() {
        return attendanceRecordRepository.findByMethodAndStatusOrderBySubmittedAtAsc(AttendanceMethod.PHOTO, AttendanceStatus.PENDING)
                .stream().map(AttendanceRecordResponse::from).toList();
    }

    /** 임원 관리 > 대면 출석 체크: 해당 날짜에 이미 존재하는 모든 출석 기록(사진/대면 무관) — 이중 처리 방지용. */
    public List<AttendanceRecordResponse> getRecordsByDate(LocalDate practiceDate) {
        return attendanceRecordRepository.findByPracticeDate(practiceDate)
                .stream().map(AttendanceRecordResponse::from).toList();
    }

    public List<AttendanceRecordResponse> getMemberCalendar(Long memberId, YearMonth yearMonth) {
        getMember(memberId);
        LocalDate from = yearMonth.atDay(1);
        LocalDate to = yearMonth.atEndOfMonth();
        return attendanceRecordRepository
                .findByMemberIdAndPracticeDateBetweenOrderByPracticeDateDescScheduledStartTimeDesc(memberId, from, to)
                .stream().map(AttendanceRecordResponse::from).toList();
    }

    public List<AttendanceRecordResponse> getMemberHistory(Long memberId) {
        getMember(memberId);
        return attendanceRecordRepository.findByMemberIdOrderByPracticeDateDescScheduledStartTimeDesc(memberId)
                .stream().map(AttendanceRecordResponse::from).toList();
    }

    private AttendanceRecord getPendingPhotoRecord(Long recordId) {
        AttendanceRecord record = attendanceRecordRepository.findById(recordId)
                .orElseThrow(() -> ApiException.notFound("존재하지 않는 인증 요청입니다. id=" + recordId));
        if (record.getMethod() != AttendanceMethod.PHOTO || !record.isPending()) {
            throw ApiException.conflict("승인/반려 가능한 상태의 사진 인증 요청이 아닙니다.");
        }
        return record;
    }

    private Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> ApiException.notFound("존재하지 않는 부원입니다. id=" + memberId));
    }
}
