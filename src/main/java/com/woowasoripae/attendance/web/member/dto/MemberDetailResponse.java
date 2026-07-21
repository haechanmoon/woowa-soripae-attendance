package com.woowasoripae.attendance.web.member.dto;

import com.woowasoripae.attendance.web.attendance.dto.AttendanceRecordResponse;
import java.util.List;

/** 임원 관리 > 대면 출석 체크 > 부원 클릭 시 뜨는 상세 시트가 쓰는 응답. */
public record MemberDetailResponse(
        Long id,
        String name,
        String position,
        String part,
        boolean officer,
        int unpaidFine,
        List<AttendanceRecordResponse> recentHistory
) {
}
