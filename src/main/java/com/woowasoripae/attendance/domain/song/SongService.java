package com.woowasoripae.attendance.domain.song;

import com.woowasoripae.attendance.domain.member.Member;
import com.woowasoripae.attendance.global.exception.ApiException;
import com.woowasoripae.attendance.web.song.dto.SongDetailResponse;
import com.woowasoripae.attendance.web.song.dto.SongSummaryResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SongService {

    private final SongRepository songRepository;
    private final SongMemberRepository songMemberRepository;

    public SongService(SongRepository songRepository, SongMemberRepository songMemberRepository) {
        this.songRepository = songRepository;
        this.songMemberRepository = songMemberRepository;
    }

    /** 합주 시간표 탭: 내가 배정된 곡 목록. */
    public List<SongSummaryResponse> getSongsForMember(Long memberId) {
        return songMemberRepository.findByMemberId(memberId).stream()
                .map(songMember -> SongSummaryResponse.from(songMember.getSong()))
                .toList();
    }

    public SongDetailResponse getSongDetail(Long songId) {
        Song song = getSong(songId);
        List<Member> members = songMemberRepository.findBySongId(songId).stream()
                .map(SongMember::getMember)
                .toList();
        return SongDetailResponse.from(song, members);
    }

    Song getSong(Long songId) {
        return songRepository.findById(songId)
                .orElseThrow(() -> ApiException.notFound("존재하지 않는 곡입니다. id=" + songId));
    }
}
