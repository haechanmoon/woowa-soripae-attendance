package com.woowasoripae.attendance.domain.song;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SongMemberRepository extends JpaRepository<SongMember, Long> {

    List<SongMember> findBySongId(Long songId);

    List<SongMember> findByMemberId(Long memberId);

    boolean existsBySongIdAndMemberId(Long songId, Long memberId);

    Optional<SongMember> findBySongIdAndMemberId(Long songId, Long memberId);
}
