package com.woowasoripae.attendance.web.member.dto;

/** 홈 탭 "나의 누적 지각/결석비" 카드가 그대로 쓰는 응답: 결석 N회(금액), 지각 총 N분(금액), 합계. */
public record FineSummaryResponse(
        Long memberId,
        int totalFine,
        long absentCount,
        int absentFine,
        int lateTotalMinutes,
        int lateFine
) {
}
