package com.woowasoripae.attendance.domain.attendance;

public enum AttendanceStatus {
    /** Photo submitted, waiting for an officer to review it. */
    PENDING,
    /** On time. */
    PRESENT,
    /** Late by 1~59 minutes. */
    LATE,
    /** Late by 60+ minutes, or a no-show. Always converted from LATE once >= absentThresholdMinutes. */
    ABSENT,
    /** Officer rejected the submitted photo; does not count as an attendance record for fine purposes. */
    REJECTED
}
