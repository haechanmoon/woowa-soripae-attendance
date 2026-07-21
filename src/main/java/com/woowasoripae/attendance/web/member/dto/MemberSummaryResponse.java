package com.woowasoripae.attendance.web.member.dto;

import com.woowasoripae.attendance.domain.member.Member;

public record MemberSummaryResponse(
        Long id, String name, String position, String part, boolean officer
) {

    public static MemberSummaryResponse from(Member member) {
        return new MemberSummaryResponse(
                member.getId(), member.getName(),
                member.getPosition(), member.getPart(), member.isOfficer());
    }
}
