package com.woowasoripae.attendance.web.schedule.dto;

import com.woowasoripae.attendance.domain.schedule.WeekScope;
import java.time.LocalDate;
import java.util.List;

/**
 * 임원 관리: 한 주(월~일)의 스케줄 등록 현황.
 * 이번 주는 마감이 지나 되돌릴 수 없는 결과이고, 다음 주는 일요일까지 아직 독려할 수 있는 대상이다.
 * 화면이 그 차이를 표현할 수 있도록 어느 주를 봤는지(scope)와 마감까지 남은 날을 함께 내려준다.
 */
public record WeekRegistrationResponse(
        WeekScope scope,
        LocalDate weekStart,
        LocalDate weekEnd,
        /** 마감까지 남은 날. 이미 마감이 지난 주(THIS)는 독려할 대상이 아니므로 null. */
        Integer daysUntilDeadline,
        /** 지금 당장 연락해야 하는 상태인지. 화면이 색 기준을 따로 정하지 않도록 서버가 판단해 내려준다. */
        boolean urgent,
        List<MemberBrief> registered,
        List<MemberBrief> notRegistered
) {
    public record MemberBrief(Long id, String name, String part) {}
}
