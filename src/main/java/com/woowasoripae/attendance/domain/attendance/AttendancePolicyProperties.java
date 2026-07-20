package com.woowasoripae.attendance.domain.attendance;

import java.time.LocalTime;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.attendance")
public record AttendancePolicyProperties(
        LocalTime coreTimeStart,
        LocalTime coreTimeEnd,
        int lateFinePerMinute,
        int absentFine,
        int absentThresholdMinutes
) {
}
