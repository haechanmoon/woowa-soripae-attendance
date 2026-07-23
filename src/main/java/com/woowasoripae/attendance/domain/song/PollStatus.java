package com.woowasoripae.attendance.domain.song;

public enum PollStatus {
    /** 후보 시간에 대해 팀원 응답을 받는 중. */
    OPEN,
    /** 보컬이 최종 시간을 확정함. */
    CONFIRMED,
    /** 취소됨. */
    CANCELED
}
