package com.woowasoripae.attendance.web.song.dto;

import com.woowasoripae.attendance.domain.member.Member;
import com.woowasoripae.attendance.domain.song.Song;
import com.woowasoripae.attendance.web.member.dto.MemberSummaryResponse;
import java.util.List;

public record SongDetailResponse(Long id, String title, String artist, List<MemberSummaryResponse> members) {

    public static SongDetailResponse from(Song song, List<Member> members) {
        return new SongDetailResponse(
                song.getId(), song.getTitle(), song.getArtist(),
                members.stream().map(MemberSummaryResponse::from).toList());
    }
}
