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

    /** dicebear avatar seed used by the frontend, e.g. https://api.dicebear.com/7.x/notionists/svg?seed={avatarSeed} */
    @Column(nullable = false, length = 50)
    private String avatarSeed;

    public Member(String name, String avatarSeed) {
        this.name = name;
        this.avatarSeed = avatarSeed;
    }
}
