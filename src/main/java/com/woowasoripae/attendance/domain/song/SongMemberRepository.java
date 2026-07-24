package com.woowasoripae.attendance.domain.song;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SongMemberRepository extends JpaRepository<SongMember, Long> {

    /** 곡에 배정된 적이 있는(=합주 대상) 부원 ID들. 중복 제거. */
    @Query("select distinct sm.member.id from SongMember sm")
    List<Long> findDistinctMemberIds();

    List<SongMember> findBySongId(Long songId);

    List<SongMember> findByMemberId(Long memberId);

    boolean existsBySongIdAndMemberId(Long songId, Long memberId);

    Optional<SongMember> findBySongIdAndMemberId(Long songId, Long memberId);
}
