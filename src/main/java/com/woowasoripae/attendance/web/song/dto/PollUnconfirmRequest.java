package com.woowasoripae.attendance.web.song.dto;

import jakarta.validation.constraints.NotNull;

/** memberId: 이 조율을 확정한(=생성한) 보컬이어야 함. */
public record PollUnconfirmRequest(
        @NotNull Long memberId
) {
}
