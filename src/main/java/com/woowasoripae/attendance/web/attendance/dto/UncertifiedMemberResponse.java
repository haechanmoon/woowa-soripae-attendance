package com.woowasoripae.attendance.web.attendance.dto;

import java.time.LocalTime;

/**
 * 임원 관리: 오늘 오기로 등록해놓고 아직 인증을 올리지 않은 부원.
 * 자동으로 결석 처리하지 않고 임원이 보고 판단하도록 목록만 제공한다 (그냥 까먹었을 수 있다).
 */
public record UncertifiedMemberResponse(
        Long memberId,
        String name,
        String part,
        LocalTime scheduledStartTime
) {
}
