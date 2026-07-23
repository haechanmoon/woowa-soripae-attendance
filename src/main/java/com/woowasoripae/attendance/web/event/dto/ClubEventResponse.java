package com.woowasoripae.attendance.web.event.dto;

import com.woowasoripae.attendance.domain.event.ClubEvent;
import java.time.LocalDate;

public record ClubEventResponse(Long id, LocalDate eventDate, String title) {
    public static ClubEventResponse from(ClubEvent event) {
        return new ClubEventResponse(event.getId(), event.getEventDate(), event.getTitle());
    }
}
