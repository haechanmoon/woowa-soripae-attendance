package com.woowasoripae.attendance.web.member.dto;

/** 임원 관리 > 정산 > 지각비 미납부자 명단이 사용하는 응답. */
public record UnpaidFineResponse(
        Long memberId,
        String name,
        String part,
        int amount
) {
}
