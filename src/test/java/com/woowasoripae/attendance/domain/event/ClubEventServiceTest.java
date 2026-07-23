package com.woowasoripae.attendance.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.woowasoripae.attendance.global.exception.ApiException;
import com.woowasoripae.attendance.web.event.dto.ClubEventRequest;
import com.woowasoripae.attendance.web.event.dto.ClubEventResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/** 하계 공연처럼 임원이 등록하는 행사 날짜. 홈 배너/캘린더 강조가 이 데이터를 그대로 쓴다. */
@ExtendWith(MockitoExtension.class)
class ClubEventServiceTest {

    @Mock
    private ClubEventRepository clubEventRepository;

    private ClubEventService clubEventService;

    private static final LocalDate CONCERT_DATE = LocalDate.of(2026, 8, 18);

    @BeforeEach
    void setUp() {
        clubEventService = new ClubEventService(clubEventRepository);
    }

    private ClubEvent eventWithId(Long id, LocalDate date, String title) {
        ClubEvent event = new ClubEvent(date, title);
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    @Test
    @DisplayName("행사를 등록하면 날짜와 제목이 그대로 저장된다")
    void createsEvent() {
        given(clubEventRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ClubEventResponse response = clubEventService.create(
                new ClubEventRequest(CONCERT_DATE, "하계 공연"));

        assertThat(response.eventDate()).isEqualTo(CONCERT_DATE);
        assertThat(response.title()).isEqualTo("하계 공연");
    }

    @Test
    @DisplayName("전체 조회는 날짜순으로 정렬된 목록을 돌려준다")
    void listsEventsByDate() {
        given(clubEventRepository.findAllByOrderByEventDateAsc()).willReturn(List.of(
                eventWithId(1L, CONCERT_DATE, "하계 공연"),
                eventWithId(2L, CONCERT_DATE.plusMonths(4), "정기 공연")));

        List<ClubEventResponse> events = clubEventService.getAll();

        assertThat(events).extracting(ClubEventResponse::title)
                .containsExactly("하계 공연", "정기 공연");
    }

    @Test
    @DisplayName("행사 날짜/제목을 수정할 수 있다")
    void updatesEvent() {
        ClubEvent event = eventWithId(1L, CONCERT_DATE, "하계 공연");
        given(clubEventRepository.findById(1L)).willReturn(Optional.of(event));

        ClubEventResponse response = clubEventService.update(
                1L, new ClubEventRequest(CONCERT_DATE.plusDays(1), "하계 공연 (장소 변경)"));

        assertThat(response.eventDate()).isEqualTo(CONCERT_DATE.plusDays(1));
        assertThat(response.title()).isEqualTo("하계 공연 (장소 변경)");
    }

    @Test
    @DisplayName("행사를 삭제한다")
    void deletesEvent() {
        ClubEvent event = eventWithId(1L, CONCERT_DATE, "하계 공연");
        given(clubEventRepository.findById(1L)).willReturn(Optional.of(event));

        clubEventService.delete(1L);

        verify(clubEventRepository).delete(event);
    }

    @Test
    @DisplayName("존재하지 않는 행사를 수정하면 404를 던진다")
    void throwsNotFoundOnUpdate() {
        given(clubEventRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> clubEventService.update(
                999L, new ClubEventRequest(CONCERT_DATE, "없는 행사")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 행사를 삭제하면 404를 던지고 아무것도 지우지 않는다")
    void throwsNotFoundOnDelete() {
        given(clubEventRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> clubEventService.delete(999L))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(clubEventRepository, never()).delete(any());
    }
}
