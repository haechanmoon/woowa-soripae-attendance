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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 팀원 한 명이 후보 시간 하나에 대해 남긴 가능/불가능 응답. */
@Getter
@Entity
@Table(
        name = "rehearsal_response",
        uniqueConstraints = @UniqueConstraint(columnNames = {"slot_id", "member_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RehearsalResponse extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    private RehearsalSlot slot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Availability availability;

    public RehearsalResponse(RehearsalSlot slot, Member member, Availability availability) {
        this.slot = slot;
        this.member = member;
        this.availability = availability;
    }

    public void update(Availability availability) {
        this.availability = availability;
    }
}
