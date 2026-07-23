package com.woowasoripae.attendance.web.song;

import com.woowasoripae.attendance.domain.song.SongService;
import com.woowasoripae.attendance.web.song.dto.SongCreateRequest;
import com.woowasoripae.attendance.web.song.dto.SongDetailResponse;
import com.woowasoripae.attendance.web.song.dto.SongSummaryResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /** 임원 관리 > 곡 관리: 등록된 전체 곡 목록. */
    @GetMapping("/api/songs")
    public List<SongSummaryResponse> getAllSongs() {
        return songService.getAllSongs();
    }

    @GetMapping("/api/songs/{songId}")
    public SongDetailResponse getSongDetail(@PathVariable Long songId) {
        return songService.getSongDetail(songId);
    }

    /** 임원 관리 > 곡 관리: 곡 등록 + 참여 부원 배정(한 번에). */
    @PostMapping("/api/songs")
    public ResponseEntity<SongDetailResponse> create(@Valid @RequestBody SongCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(songService.create(request));
    }

    /** 임원 관리 > 곡 관리: 참여 부원 추가. */
    @PostMapping("/api/songs/{songId}/members/{memberId}")
    public SongDetailResponse addMember(@PathVariable Long songId, @PathVariable Long memberId) {
        return songService.addMember(songId, memberId);
    }

    /** 임원 관리 > 곡 관리: 잘못 배정한 참여 부원 제거. */
    @DeleteMapping("/api/songs/{songId}/members/{memberId}")
    public SongDetailResponse removeMember(@PathVariable Long songId, @PathVariable Long memberId) {
        return songService.removeMember(songId, memberId);
    }
}
