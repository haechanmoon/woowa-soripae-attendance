package com.woowasoripae.attendance.web.song;

import com.woowasoripae.attendance.domain.song.RehearsalPollService;
import com.woowasoripae.attendance.web.song.dto.PollConfirmRequest;
import com.woowasoripae.attendance.web.song.dto.PollCreateRequest;
import com.woowasoripae.attendance.web.song.dto.PollResponse;
import com.woowasoripae.attendance.web.song.dto.SlotResponseRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RehearsalPollController {

    private final RehearsalPollService rehearsalPollService;

    public RehearsalPollController(RehearsalPollService rehearsalPollService) {
        this.rehearsalPollService = rehearsalPollService;
    }

    /** 보컬이 후보 시간을 올려 새 조율을 연다. */
    @PostMapping("/api/songs/{songId}/polls")
    public ResponseEntity<PollResponse> create(@PathVariable Long songId, @Valid @RequestBody PollCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rehearsalPollService.createPoll(songId, request));
    }

    /** 합주 시간표 탭: 곡의 가장 최근 조율 조회. */
    @GetMapping("/api/songs/{songId}/polls/latest")
    public PollResponse getLatest(@PathVariable Long songId) {
        return rehearsalPollService.getLatestPoll(songId);
    }

    /** 팀원이 후보 시간에 가능/불가능 응답(탭으로 토글). */
    @PutMapping("/api/polls/{pollId}/slots/{slotId}/responses")
    public PollResponse respond(
            @PathVariable Long pollId, @PathVariable Long slotId, @Valid @RequestBody SlotResponseRequest request
    ) {
        return rehearsalPollService.respond(pollId, slotId, request);
    }

    /** 보컬이 최종 시간을 확정. */
    @PostMapping("/api/polls/{pollId}/confirm")
    public PollResponse confirm(@PathVariable Long pollId, @Valid @RequestBody PollConfirmRequest request) {
        return rehearsalPollService.confirm(pollId, request);
    }
}
