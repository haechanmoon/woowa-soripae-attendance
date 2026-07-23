package com.woowasoripae.attendance.domain.song;

import com.woowasoripae.attendance.domain.member.Member;
import com.woowasoripae.attendance.domain.member.MemberRepository;
import com.woowasoripae.attendance.global.exception.ApiException;
import com.woowasoripae.attendance.web.song.dto.SongCreateRequest;
import com.woowasoripae.attendance.web.song.dto.SongDetailResponse;
import com.woowasoripae.attendance.web.song.dto.SongSummaryResponse;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SongService {

    /** 곡 상세에서 참여자를 보여주는 순서. 목록에 없는 파트(매니저 등)는 뒤로 밀린다. */
    private static final List<String> PART_DISPLAY_ORDER = List.of("보컬", "세션", "카혼");

    private final SongRepository songRepository;
    private final SongMemberRepository songMemberRepository;
    private final MemberRepository memberRepository;

    public SongService(SongRepository songRepository, SongMemberRepository songMemberRepository, MemberRepository memberRepository) {
        this.songRepository = songRepository;
        this.songMemberRepository = songMemberRepository;
        this.memberRepository = memberRepository;
    }

    /** 합주 시간표 탭: 내가 배정된 곡 목록. */
    public List<SongSummaryResponse> getSongsForMember(Long memberId) {
        return songMemberRepository.findByMemberId(memberId).stream()
                .map(songMember -> SongSummaryResponse.from(songMember.getSong()))
                .toList();
    }

    /** 임원 관리 > 곡 관리: 등록된 전체 곡 목록. */
    public List<SongSummaryResponse> getAllSongs() {
        return songRepository.findAll().stream().map(SongSummaryResponse::from).toList();
    }

    public SongDetailResponse getSongDetail(Long songId) {
        Song song = getSong(songId);
        return SongDetailResponse.from(song, membersOf(songId));
    }

    /** 임원 관리 > 곡 관리: 곡을 새로 등록하고 참여 부원을 배정한다. */
    @Transactional
    public SongDetailResponse create(SongCreateRequest request) {
        Song song = songRepository.save(new Song(request.title(), request.artist()));
        List<Long> memberIds = request.memberIds() == null ? List.of() : request.memberIds();
        memberIds.forEach(memberId -> songMemberRepository.save(new SongMember(song, getMember(memberId))));
        return SongDetailResponse.from(song, membersOf(song.getId()));
    }

    /** 임원 관리 > 곡 관리: 참여 부원 추가. */
    @Transactional
    public SongDetailResponse addMember(Long songId, Long memberId) {
        Song song = getSong(songId);
        if (!songMemberRepository.existsBySongIdAndMemberId(songId, memberId)) {
            songMemberRepository.save(new SongMember(song, getMember(memberId)));
        }
        return SongDetailResponse.from(song, membersOf(songId));
    }

    /** 임원 관리 > 곡 관리: 잘못 배정한 참여 부원 제거. */
    @Transactional
    public SongDetailResponse removeMember(Long songId, Long memberId) {
        Song song = getSong(songId);
        songMemberRepository.findBySongIdAndMemberId(songId, memberId)
                .ifPresent(songMemberRepository::delete);
        return SongDetailResponse.from(song, membersOf(songId));
    }

    /** 곡 참여자를 보컬 > 세션 > 카혼 > 그 외 순서로 정렬한다. */
    private List<Member> membersOf(Long songId) {
        Comparator<Member> byPartOrder = Comparator.comparingInt(member -> {
            int index = PART_DISPLAY_ORDER.indexOf(member.getPart());
            return index == -1 ? PART_DISPLAY_ORDER.size() : index;
        });
        return songMemberRepository.findBySongId(songId).stream()
                .map(SongMember::getMember)
                .sorted(byPartOrder)
                .toList();
    }

    Song getSong(Long songId) {
        return songRepository.findById(songId)
                .orElseThrow(() -> ApiException.notFound("존재하지 않는 곡입니다. id=" + songId));
    }

    private Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> ApiException.notFound("존재하지 않는 부원입니다. id=" + memberId));
    }
}
