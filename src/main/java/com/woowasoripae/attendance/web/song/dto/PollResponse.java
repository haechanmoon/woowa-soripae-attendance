package com.woowasoripae.attendance.web.song.dto;

import com.woowasoripae.attendance.domain.song.PollStatus;
import java.time.LocalDateTime;
import java.util.List;

public record PollResponse(
        Long pollId,
        Long songId,
        String songTitle,
        PollStatus status,
        Long createdByMemberId,
        String createdByName,
        List<SlotDto> slots
) {
    public record SlotDto(
            Long slotId,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean confirmed,
            List<MemberResponseDto> responses
    ) {
    }

    /** availability: "AVAILABLE" / "UNAVAILABLE" / null(아직 응답 안 함). */
    public record MemberResponseDto(
            Long memberId,
            String memberName,
            String availability
    ) {
    }
}
