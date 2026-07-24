package com.woowasoripae.attendance.web.schedule.dto;

import java.time.LocalDate;
import java.util.List;

/** 임원 관리: 다음 주(다음 월~일) 스케줄 등록 현황. 아직 등록 안 한 부원을 파악하기 위함. */
public record NextWeekRegistrationResponse(
        LocalDate weekStart,
        LocalDate weekEnd,
        List<MemberBrief> registered,
        List<MemberBrief> notRegistered
) {
    public record MemberBrief(Long id, String name, String part) {}
}
