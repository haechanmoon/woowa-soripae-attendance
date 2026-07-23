package com.woowasoripae.attendance.web.song.dto;

import com.woowasoripae.attendance.domain.song.Song;

public record SongSummaryResponse(Long id, String title, String artist) {

    public static SongSummaryResponse from(Song song) {
        return new SongSummaryResponse(song.getId(), song.getTitle(), song.getArtist());
    }
}
