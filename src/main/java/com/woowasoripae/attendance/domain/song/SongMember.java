package com.woowasoripae.attendance.domain.song;

import com.woowasoripae.attendance.domain.member.Member;
import com.woowasoripae.attendance.global.entity.BaseTimeEntity;
import jakarta.persistence.Entity;
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

/** 곡 하나에 부원 한 명을 배정한다(팀 구성). */
@Getter
@Entity
@Table(
        name = "song_member",
        uniqueConstraints = @UniqueConstraint(columnNames = {"song_id", "member_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SongMember extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    public SongMember(Song song, Member member) {
        this.song = song;
        this.member = member;
    }
}
