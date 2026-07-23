package com.woowasoripae.attendance.web.song.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/** creatorMemberId: 반드시 해당 곡에 배정된 보컬이어야 함. slots: 후보 시간 목록(1개 이상). */
public record PollCreateRequest(
        @NotNull Long creatorMemberId,
        @NotEmpty List<@Valid SlotRequest> slots
) {
    public record SlotRequest(
            @NotNull LocalDateTime startAt,
            @NotNull LocalDateTime endAt
    ) {
    }
}
