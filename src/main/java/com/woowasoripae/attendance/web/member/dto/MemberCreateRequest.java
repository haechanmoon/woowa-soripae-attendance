package com.woowasoripae.attendance.web.member.dto;

import jakarta.validation.constraints.NotBlank;

/** position이 없으면(null/blank) 일반 부원, 있으면 임원(회장/부회장/총무/서기/홍보 등)으로 등록된다. */
public record MemberCreateRequest(
        @NotBlank String name,
        String position,
        @NotBlank String part
) {
}
