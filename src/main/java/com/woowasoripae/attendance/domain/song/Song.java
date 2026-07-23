package com.woowasoripae.attendance.domain.song;

import com.woowasoripae.attendance.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이번 시즌 합주 레퍼토리 한 곡. 원곡 아티스트는 참고용 표기일 뿐, 실제 커버는 song_member로 배정된다. */
@Getter
@Entity
@Table(name = "song")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Song extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 100)
    private String artist;

    public Song(String title, String artist) {
        this.title = title;
        this.artist = artist;
    }
}
