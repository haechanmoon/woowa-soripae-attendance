package com.woowasoripae.attendance.domain.attendance;

import com.woowasoripae.attendance.domain.member.Member;
import com.woowasoripae.attendance.domain.member.MemberRepository;
import com.woowasoripae.attendance.global.exception.ApiException;
import com.woowasoripae.attendance.global.file.FileStorageService;
import com.woowasoripae.attendance.web.attendance.dto.ApproveAttendanceRequest;
import com.woowasoripae.attendance.web.attendance.dto.AttendanceRecordResponse;
import com.woowasoripae.attendance.web.attendance.dto.FaceCheckRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final MemberRepository memberRepository;
    private final FineCalculator fineCalculator;
    private final FileStorageService fileStorageService;

    public AttendanceService(
            AttendanceRecordRepository attendanceRecordRepository,
            MemberRepository memberRepository,
            FineCalculator fineCalculator,
            FileStorageService fileStorageService
    ) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.memberRepository = memberRepository;
        this.fineCalculator = fineCalculator;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public AttendanceRecordResponse submitPhoto(Long memberId, LocalTime scheduledStartTime, MultipartFile photo) {
        Member member = getMember(memberId);
        LocalDateTime submittedAt = LocalDateTime.now();
        LocalDate practiceDate = submittedAt.toLocalDate();
        LocalTime scheduledEndTime = scheduledStartTime.plusHours(2);

        attendanceRecordRepository.findByMemberIdAndPracticeDateAndScheduledStartTime(memberId, practiceDate, scheduledStartTime)
                .ifPresent(existing -> {
                    throw ApiException.conflict("이미 해당 스케줄로 인증을 제출했습니다.");
                });

        String photoUrl = fileStorageService.store(photo);
        AttendanceRecord record = AttendanceRecord.createPendingPhotoSubmission(
                member, practiceDate, scheduledStartTime, scheduledEndTime, photoUrl, submittedAt);

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
                .findByMemberIdAndPracticeDateAndScheduledStartTime(request.memberId(), request.practiceDate(), request.scheduledStartTime())
                .orElseGet(() -> AttendanceRecord.createFaceToFaceDecision(
                        member, request.practiceDate(), request.scheduledStartTime(), request.scheduledEndTime(), evaluation, now));

        if (record.getId() != null) {
            record.applyDecision(evaluation, now);
        }

        return AttendanceRecordResponse.from(attendanceRecordRepository.save(record));
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
