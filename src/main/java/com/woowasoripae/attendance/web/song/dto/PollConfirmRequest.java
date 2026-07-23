package com.woowasoripae.attendance.web.song.dto;

import jakarta.validation.constraints.NotNull;

/** memberId: 이 조율을 만든 보컬이어야 함. slotId: 확정할 후보 시간. */
public record PollConfirmRequest(
        @NotNull Long memberId,
        @NotNull Long slotId
) {
}
