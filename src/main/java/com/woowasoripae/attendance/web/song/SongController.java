package com.woowasoripae.attendance.web.song;

import com.woowasoripae.attendance.domain.song.SongService;
import com.woowasoripae.attendance.web.song.dto.SongDetailResponse;
import com.woowasoripae.attendance.web.song.dto.SongSummaryResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SongController {

    private final SongService songService;

    public SongController(SongService songService) {
        this.songService = songService;
    }

    /** 합주 시간표 탭: 내가 배정된 곡 목록. */
    @GetMapping("/api/members/{memberId}/songs")
    public List<SongSummaryResponse> getSongsForMember(@PathVariable Long memberId) {
        return songService.getSongsForMember(memberId);
    }

    @GetMapping("/api/songs/{songId}")
    public SongDetailResponse getSongDetail(@PathVariable Long songId) {
        return songService.getSongDetail(songId);
    }
}
