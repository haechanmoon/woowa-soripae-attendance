package com.woowasoripae.attendance.domain.member;

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

@Getter
@Entity
@Table(name = "member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String name;

    /** 임원 직책 (회장/부회장/총무/서기/홍보 등). null이면 일반 부원. */
    @Column(length = 20)
    private String position;

    /** 합주 파트 (보컬/세션/카혼/매니저 등). */
    @Column(nullable = false, length = 20)
    private String part;

    public Member(String name, String position, String part) {
        this.name = name;
        this.position = position;
        this.part = part;
    }

    public boolean isOfficer() {
        return position != null;
    }
}
