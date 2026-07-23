package com.woowasoripae.attendance.web.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ClubEventRequest(
        @NotNull LocalDate eventDate,
        @NotBlank String title
) {
}
