package com.woowasoripae.attendance.web.song.dto;

import com.woowasoripae.attendance.domain.song.Availability;
import jakarta.validation.constraints.NotNull;

public record SlotResponseRequest(
        @NotNull Long memberId,
        @NotNull Availability availability
) {
}
