package com.woowasoripae.attendance.domain.song;

import com.woowasoripae.attendance.domain.member.Member;
import com.woowasoripae.attendance.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 한 곡에 대한 합주 시간 조율 한 라운드. 보컬만 열 수 있고, 보컬만 확정할 수 있다. */
@Getter
@Entity
@Table(name = "rehearsal_poll")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RehearsalPoll extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_member_id", nullable = false)
    private Member createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PollStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_slot_id")
    private RehearsalSlot confirmedSlot;

    public RehearsalPoll(Song song, Member createdBy) {
        this.song = song;
        this.createdBy = createdBy;
        this.status = PollStatus.OPEN;
    }

    public void confirm(RehearsalSlot slot) {
        if (this.status != PollStatus.OPEN) {
            throw new IllegalStateException("OPEN 상태의 조율만 확정할 수 있습니다.");
        }
        this.status = PollStatus.CONFIRMED;
        this.confirmedSlot = slot;
    }

    /** 확정을 취소하고 다시 OPEN 상태로 되돌린다. 기존 후보 시간/투표는 그대로 남아 다른 시간으로 바로 재확정할 수 있다. */
    public void unconfirm() {
        if (this.status != PollStatus.CONFIRMED) {
            throw new IllegalStateException("확정된 조율만 취소할 수 있습니다.");
        }
        this.status = PollStatus.OPEN;
        this.confirmedSlot = null;
    }

    public boolean isOpen() {
        return this.status == PollStatus.OPEN;
    }
}
