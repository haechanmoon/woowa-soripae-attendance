package com.woowasoripae.attendance.web.attendance.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 임원 관리: 지난 두 주 안에서 등록해놓고 아직 인증/대면 체크가 안 된 날짜 하나의 묶음.
 * 날짜를 하나하나 넘겨봐야만 알 수 있던 미인증을, 넘기지 않고도 모아 볼 수 있게 한다.
 */
public record MissedAttendanceDateResponse(
        LocalDate practiceDate,
        List<UncertifiedMemberResponse> members
) {
}
