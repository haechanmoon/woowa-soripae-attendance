package com.woowasoripae.attendance.web.attendance;

import com.woowasoripae.attendance.domain.attendance.AttendanceService;
import com.woowasoripae.attendance.web.attendance.dto.ApproveAttendanceRequest;
import com.woowasoripae.attendance.web.attendance.dto.AttendanceRecordResponse;
import com.woowasoripae.attendance.web.attendance.dto.FaceCheckRequest;
import jakarta.validation.Valid;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    /** 인증하기 탭 > "인증 요청하기": 사진 업로드로 출석을 제출한다 (PENDING으로 시작, 임원 승인 대기). */
    @PostMapping(value = "/api/attendance-records", consumes = "multipart/form-data")
    public ResponseEntity<AttendanceRecordResponse> submitPhoto(
            @RequestParam Long memberId,
            @RequestParam LocalTime scheduledStartTime,
            @RequestParam MultipartFile photo
    ) {
        AttendanceRecordResponse response = attendanceService.submitPhoto(memberId, scheduledStartTime, photo);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 임원 관리 > 사진 승인 대기열: 승인 확정. 출석 상태 변경(저장) API — lateMinutes 60 이상이면 자동 결석 전환. */
    @PatchMapping("/api/attendance-records/{recordId}/approve")
    public AttendanceRecordResponse approve(@PathVariable Long recordId, @Valid @RequestBody ApproveAttendanceRequest request) {
        return attendanceService.approve(recordId, request);
    }

    /** 임원 관리 > 사진 승인 대기열: 반려. */
    @PatchMapping("/api/attendance-records/{recordId}/reject")
    public AttendanceRecordResponse reject(@PathVariable Long recordId) {
        return attendanceService.reject(recordId);
    }

    /** 임원 관리 > 대면 출석 체크: 출석/지각(분)/결석 클릭 시 즉시 저장. 출석 상태 변경(저장) API. */
    @PutMapping("/api/attendance-records/face-check")
    public AttendanceRecordResponse faceCheck(@Valid @RequestBody FaceCheckRequest request) {
        return attendanceService.faceCheck(request);
    }

    /** 임원 관리 > 사진 승인 대기열 목록. */
    @GetMapping("/api/attendance-records/pending")
    public List<AttendanceRecordResponse> getPendingQueue() {
        return attendanceService.getPendingPhotoQueue();
    }

    /** 홈 탭 캘린더: 월별 내 합주 기록. */
    @GetMapping("/api/members/{memberId}/attendance-records")
    public List<AttendanceRecordResponse> getMemberCalendar(
            @PathVariable Long memberId,
            @RequestParam int year,
            @RequestParam int month
    ) {
        return attendanceService.getMemberCalendar(memberId, YearMonth.of(year, month));
    }

    /** 인증하기 탭 > 내 인증 내역: 전체 이력(최신순). */
    @GetMapping("/api/members/{memberId}/attendance-records/history")
    public List<AttendanceRecordResponse> getMemberHistory(@PathVariable Long memberId) {
        return attendanceService.getMemberHistory(memberId);
    }
}
