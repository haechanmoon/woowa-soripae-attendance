package com.woowasoripae.attendance.web.event;

import com.woowasoripae.attendance.domain.event.ClubEventService;
import com.woowasoripae.attendance.web.event.dto.ClubEventRequest;
import com.woowasoripae.attendance.web.event.dto.ClubEventResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClubEventController {

    private final ClubEventService clubEventService;

    public ClubEventController(ClubEventService clubEventService) {
        this.clubEventService = clubEventService;
    }

    /** 홈 배너·캘린더 강조에 쓰는 전체 행사 목록. */
    @GetMapping("/api/events")
    public List<ClubEventResponse> getAll() {
        return clubEventService.getAll();
    }

    /** 임원 관리 > 행사 관리: 새 행사 등록(예: 하계 공연). */
    @PostMapping("/api/events")
    public ResponseEntity<ClubEventResponse> create(@Valid @RequestBody ClubEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clubEventService.create(request));
    }

    /** 임원 관리 > 행사 관리: 날짜/제목 수정. */
    @PatchMapping("/api/events/{eventId}")
    public ClubEventResponse update(@PathVariable Long eventId, @Valid @RequestBody ClubEventRequest request) {
        return clubEventService.update(eventId, request);
    }

    /** 임원 관리 > 행사 관리: 삭제. */
    @DeleteMapping("/api/events/{eventId}")
    public ResponseEntity<Void> delete(@PathVariable Long eventId) {
        clubEventService.delete(eventId);
        return ResponseEntity.noContent().build();
    }
}
