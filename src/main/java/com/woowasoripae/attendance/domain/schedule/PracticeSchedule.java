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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A member's registered slot for an upcoming practice date.
 * 합주를 몇 시간 하든 출석은 하루 한 번이면 인정되므로 종료 시각은 두지 않고 시작 시각만 기록한다.
 */
@Getter
@Entity
@Table(
        name = "practice_schedule",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "practice_date"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PracticeSchedule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "practice_date", nullable = false)
    private LocalDate practiceDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    public PracticeSchedule(Member member, LocalDate practiceDate, LocalTime startTime) {
        this.member = member;
        this.practiceDate = practiceDate;
        this.startTime = startTime;
    }

    /** 하루 한 타임이라 시간을 바꾸는 건 지웠다 만드는 게 아니라 그 자리를 옮기는 일이다. */
    public void changeStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }
}
