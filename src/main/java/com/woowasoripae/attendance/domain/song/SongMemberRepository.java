package com.woowasoripae.attendance.domain.song;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SongMemberRepository extends JpaRepository<SongMember, Long> {

    List<SongMember> findBySongId(Long songId);

    List<SongMember> findByMemberId(Long memberId);

    boolean existsBySongIdAndMemberId(Long songId, Long memberId);
}
