package com.woowasoripae.attendance.web.member.dto;

import com.woowasoripae.attendance.domain.member.Member;

public record MemberSummaryResponse(Long id, String name, String avatarSeed) {

    public static MemberSummaryResponse from(Member member) {
        return new MemberSummaryResponse(member.getId(), member.getName(), member.getAvatarSeed());
    }
}
