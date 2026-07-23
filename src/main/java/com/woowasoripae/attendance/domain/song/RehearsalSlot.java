package com.woowasoripae.attendance.domain.song;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 조율(RehearsalPoll) 하나가 올린 후보 시간 하나. */
@Getter
@Entity
@Table(name = "rehearsal_slot")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RehearsalSlot extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_id", nullable = false)
    private RehearsalPoll poll;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    public RehearsalSlot(RehearsalPoll poll, LocalDateTime startAt, LocalDateTime endAt) {
        this.poll = poll;
        this.startAt = startAt;
        this.endAt = endAt;
    }
}
