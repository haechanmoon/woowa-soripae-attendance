package com.woowasoripae.attendance.web.song.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record SongCreateRequest(
        @NotBlank String title,
        String artist,
        List<Long> memberIds
) {
}
