package com.woowasoripae.attendance.web.schedule;

import com.woowasoripae.attendance.domain.schedule.ScheduleService;
import com.woowasoripae.attendance.domain.schedule.WeekScope;
import com.woowasoripae.attendance.web.schedule.dto.ScheduleChangeLogResponse;
import com.woowasoripae.attendance.web.schedule.dto.ScheduleRegisterRequest;
import com.woowasoripae.attendance.web.schedule.dto.ScheduleResponse;
import com.woowasoripae.attendance.web.schedule.dto.ThisWeekChangeRequest;
import com.woowasoripae.attendance.web.schedule.dto.WeekRegistrationResponse;
import com.woowasoripae.attendance.web.schedule.dto.WeeklyScheduleResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping("/api/members/{memberId}/schedules")
    public List<ScheduleResponse> getSchedules(@PathVariable Long memberId) {
        return scheduleService.getUpcomingSchedules(memberId);
    }

    /** 임원 관리 > 대면 출석 체크: 특정 날짜(기본 오늘)에 등록해 놓은 사람 목록. */
    @GetMapping("/api/schedules")
    public List<ScheduleResponse> getSchedulesByDate(@RequestParam(required = false) LocalDate date) {
        return scheduleService.getSchedulesByDate(date != null ? date : LocalDate.now());
    }

    /** 요일별로 누가 언제 오는지 한 주(월~일) 단위로 본다. 부원 모두가 볼 수 있다. */
    @GetMapping("/api/schedules/weekly")
    public WeeklyScheduleResponse getWeeklySchedule(@RequestParam(defaultValue = "THIS") WeekScope scope) {
        return scheduleService.getWeeklySchedule(scope);
    }

    /** 임원 관리: 스케줄을 아직 등록하지 않은 부원 현황. scope를 비우면 오늘 요일에 맞는 주를 서버가 고른다. */
    @GetMapping("/api/schedules/registration")
    public WeekRegistrationResponse getRegistrationStatus(@RequestParam(required = false) WeekScope scope) {
        return scheduleService.getRegistrationStatus(scope);
    }

    /** 임원 관리: 마감 후 이번 주 스케줄을 바꾼 내역. 승인 대상이 아니라 열람용이다. */
    @GetMapping("/api/schedules/this-week-changes")
    public List<ScheduleChangeLogResponse> getThisWeekChanges() {
        return scheduleService.getThisWeekChanges();
    }

    /** 마감이 지난 이번 주 스케줄을 사유와 함께 바꾼다. startTime을 비우면 그날 등록을 취소한다. */
    @PostMapping("/api/members/{memberId}/schedules/this-week")
    public ResponseEntity<ScheduleResponse> changeThisWeek(
            @PathVariable Long memberId, @Valid @RequestBody ThisWeekChangeRequest request) {
        return ResponseEntity.ok(scheduleService.changeThisWeek(memberId, request));
    }

    @PostMapping("/api/members/{memberId}/schedules")
    public ResponseEntity<ScheduleResponse> register(@PathVariable Long memberId, @Valid @RequestBody ScheduleRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.register(memberId, request));
    }

    @DeleteMapping("/api/members/{memberId}/schedules/{scheduleId}")
    public ResponseEntity<Void> delete(@PathVariable Long memberId, @PathVariable Long scheduleId) {
        scheduleService.delete(memberId, scheduleId);
        return ResponseEntity.noContent().build();
    }
}
