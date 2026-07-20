package com.woowasoripae.attendance.domain.attendance;

public enum AttendanceMethod {
    /** Member self-certifies by uploading a photo; requires officer review (PENDING -> PRESENT/LATE/ABSENT/REJECTED). */
    PHOTO,
    /** Officer marks the member's status directly during in-person roll call; decided immediately, no PENDING state. */
    FACE_TO_FACE
}
