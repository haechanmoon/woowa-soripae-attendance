package com.woowasoripae.attendance.domain.event;

import com.woowasoripae.attendance.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 하계 공연처럼 임원이 등록하는 중요 행사 날짜. 홈 배너 D-day와 캘린더 강조 표시에 쓰인다. */
@Getter
@Entity
@Table(name = "club_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClubEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate eventDate;

    @Column(nullable = false, length = 100)
    private String title;

    public ClubEvent(LocalDate eventDate, String title) {
        this.eventDate = eventDate;
        this.title = title;
    }

    public void update(LocalDate eventDate, String title) {
        this.eventDate = eventDate;
        this.title = title;
    }
}
