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

/** A member's registered slot for an upcoming practice date. Always exactly 2 hours long. */
@Getter
@Entity
@Table(
        name = "practice_schedule",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "practice_date", "start_time"})
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

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    public PracticeSchedule(Member member, LocalDate practiceDate, LocalTime startTime) {
        this.member = member;
        this.practiceDate = practiceDate;
        this.startTime = startTime;
        this.endTime = startTime.plusHours(2);
    }
}
