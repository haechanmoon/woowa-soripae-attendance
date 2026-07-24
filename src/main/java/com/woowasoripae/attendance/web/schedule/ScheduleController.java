package com.woowasoripae.attendance.web.schedule;

import com.woowasoripae.attendance.domain.schedule.ScheduleService;
import com.woowasoripae.attendance.web.schedule.dto.NextWeekRegistrationResponse;
import com.woowasoripae.attendance.web.schedule.dto.ScheduleRegisterRequest;
import com.woowasoripae.attendance.web.schedule.dto.ScheduleResponse;
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

    /** 임원 관리: 다음 주 스케줄을 아직 등록하지 않은 부원 현황. */
    @GetMapping("/api/schedules/next-week-registration")
    public NextWeekRegistrationResponse getNextWeekRegistration() {
        return scheduleService.getNextWeekRegistration();
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
