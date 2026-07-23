package com.woowasoripae.attendance.domain.event;

import com.woowasoripae.attendance.global.exception.ApiException;
import com.woowasoripae.attendance.web.event.dto.ClubEventRequest;
import com.woowasoripae.attendance.web.event.dto.ClubEventResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ClubEventService {

    private final ClubEventRepository clubEventRepository;

    public ClubEventService(ClubEventRepository clubEventRepository) {
        this.clubEventRepository = clubEventRepository;
    }

    /** 홈 배너(다가오는 행사)와 캘린더 강조 표시에 쓰는 전체 행사 목록(날짜순). */
    public List<ClubEventResponse> getAll() {
        return clubEventRepository.findAllByOrderByEventDateAsc()
                .stream().map(ClubEventResponse::from).toList();
    }

    @Transactional
    public ClubEventResponse create(ClubEventRequest request) {
        ClubEvent event = new ClubEvent(request.eventDate(), request.title());
        return ClubEventResponse.from(clubEventRepository.save(event));
    }

    @Transactional
    public ClubEventResponse update(Long eventId, ClubEventRequest request) {
        ClubEvent event = getEvent(eventId);
        event.update(request.eventDate(), request.title());
        return ClubEventResponse.from(event);
    }

    @Transactional
    public void delete(Long eventId) {
        clubEventRepository.delete(getEvent(eventId));
    }

    private ClubEvent getEvent(Long eventId) {
        return clubEventRepository.findById(eventId)
                .orElseThrow(() -> ApiException.notFound("존재하지 않는 행사입니다. id=" + eventId));
    }
}
