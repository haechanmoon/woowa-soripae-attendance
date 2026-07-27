package com.woowasoripae.attendance.domain.schedule;

import com.woowasoripae.attendance.domain.member.Member;
import com.woowasoripae.attendance.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 마감(일요일)이 지난 뒤 이번 주 스케줄을 바꾼 흔적.
 * 변경 자체는 승인 없이 바로 반영된다. 막으면 "못 가게 됐어요"가 조용한 회피가 되고,
 * 승인을 기다리게 하면 합주를 다녀와도 인증을 못 하기 때문이다. 대신 사유와 함께 여기 남아 임원이 사후에 본다.
 */
@Getter
@Entity
@Table(name = "schedule_change_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleChangeLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "practice_date", nullable = false)
    private LocalDate practiceDate;

    /** 바꾸기 전 시각. 새로 등록한 경우 비어 있다. */
    @Column(name = "previous_start_time")
    private LocalTime previousStartTime;

    /** 바꾼 뒤 시각. 등록을 취소한 경우 비어 있다. */
    @Column(name = "new_start_time")
    private LocalTime newStartTime;

    /** 임원이 판단할 근거가 이것뿐이라 반드시 받는다. */
    @Column(nullable = false, length = 200)
    private String reason;

    public ScheduleChangeLog(Member member, LocalDate practiceDate,
            LocalTime previousStartTime, LocalTime newStartTime, String reason) {
        this.member = member;
        this.practiceDate = practiceDate;
        this.previousStartTime = previousStartTime;
        this.newStartTime = newStartTime;
        this.reason = reason;
    }

    /** 무엇이 일어난 변경인지는 따로 저장하지 않고 남은 두 시각으로 판별한다. 상태가 하나뿐이라 어긋날 수 없다. */
    public Kind kind() {
        if (previousStartTime == null) {
            return Kind.ADDED;
        }
        return newStartTime == null ? Kind.CANCELED : Kind.MOVED;
    }

    public enum Kind {
        /** 마감을 놓쳤다가 뒤늦게 올림 */
        ADDED,
        /** 이미 올린 시각을 옮김 */
        MOVED,
        /** 가기로 했다가 취소함 — 임원이 가장 눈여겨봐야 할 쪽 */
        CANCELED
    }
}
