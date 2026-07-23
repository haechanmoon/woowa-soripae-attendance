package com.woowasoripae.attendance.domain.song;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.woowasoripae.attendance.domain.member.Member;
import com.woowasoripae.attendance.domain.member.MemberRepository;
import com.woowasoripae.attendance.global.exception.ApiException;
import com.woowasoripae.attendance.web.song.dto.PollConfirmRequest;
import com.woowasoripae.attendance.web.song.dto.PollCreateRequest;
import com.woowasoripae.attendance.web.song.dto.PollResponse;
import com.woowasoripae.attendance.web.song.dto.PollUnconfirmRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 합주 시간 조율(when2meet-lite). "보컬만 열고 확정할 수 있다"는 권한 규칙과
 * 확정 취소(unconfirm) 동작이 핵심 검증 대상이다.
 */
@ExtendWith(MockitoExtension.class)
class RehearsalPollServiceTest {

    @Mock
    private SongRepository songRepository;
    @Mock
    private SongMemberRepository songMemberRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private RehearsalPollRepository pollRepository;
    @Mock
    private RehearsalSlotRepository slotRepository;
    @Mock
    private RehearsalResponseRepository responseRepository;

    private RehearsalPollService pollService;

    private Song song;
    private Member vocal;
    private Member session;

    private static final LocalDateTime SLOT_START = LocalDateTime.of(2026, 8, 1, 13, 0);

    @BeforeEach
    void setUp() {
        pollService = new RehearsalPollService(
                songRepository, songMemberRepository, memberRepository,
                pollRepository, slotRepository, responseRepository);

        song = new Song("그러나", null);
        ReflectionTestUtils.setField(song, "id", 1L);

        vocal = memberWithId(1L, "김호", "보컬");
        session = memberWithId(2L, "지용혁", "세션");
    }

    private Member memberWithId(Long id, String name, String part) {
        Member member = new Member(name, null, part);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private RehearsalSlot slotWithId(Long id, RehearsalPoll poll) {
        RehearsalSlot slot = new RehearsalSlot(poll, SLOT_START, SLOT_START.plusHours(1));
        ReflectionTestUtils.setField(slot, "id", id);
        return slot;
    }

    private RehearsalPoll pollWithId(Long id, Member createdBy) {
        RehearsalPoll poll = new RehearsalPoll(song, createdBy);
        ReflectionTestUtils.setField(poll, "id", id);
        return poll;
    }

    @Nested
    @DisplayName("createPoll")
    class CreatePoll {

        private PollCreateRequest requestBy(Long memberId) {
            return new PollCreateRequest(memberId, List.of(
                    new PollCreateRequest.SlotRequest(SLOT_START, SLOT_START.plusHours(1))));
        }

        @Test
        @DisplayName("보컬이 후보 시간을 올리면 OPEN 상태의 조율이 생성된다")
        void vocalCanOpenPoll() {
            given(songRepository.findById(1L)).willReturn(Optional.of(song));
            given(memberRepository.findById(1L)).willReturn(Optional.of(vocal));
            given(songMemberRepository.existsBySongIdAndMemberId(1L, 1L)).willReturn(true);
            given(pollRepository.findFirstBySongIdAndStatusOrderByIdDesc(1L, PollStatus.OPEN))
                    .willReturn(Optional.empty());
            given(pollRepository.save(any())).willAnswer(inv -> {
                RehearsalPoll p = inv.getArgument(0);
                ReflectionTestUtils.setField(p, "id", 100L);
                return p;
            });
            // 실제 save()는 id를 채워주므로 목에서도 동일하게 흉내낸다(id가 null이면 응답 변환에서 NPE).
            given(slotRepository.save(any())).willAnswer(inv -> {
                RehearsalSlot slot = inv.getArgument(0);
                ReflectionTestUtils.setField(slot, "id", 200L);
                return slot;
            });
            given(songMemberRepository.findBySongId(1L)).willReturn(List.of(new SongMember(song, vocal)));
            given(responseRepository.findBySlot_Poll_Id(100L)).willReturn(List.of());

            PollResponse response = pollService.createPoll(1L, requestBy(1L));

            assertThat(response.status()).isEqualTo(PollStatus.OPEN);
            assertThat(response.createdByMemberId()).isEqualTo(1L);
            assertThat(response.slots()).hasSize(1);
        }

        @Test
        @DisplayName("보컬이 아닌 팀원은 조율을 열 수 없다")
        void nonVocalCannotOpenPoll() {
            given(songRepository.findById(1L)).willReturn(Optional.of(song));
            given(memberRepository.findById(2L)).willReturn(Optional.of(session));
            given(songMemberRepository.existsBySongIdAndMemberId(1L, 2L)).willReturn(true);

            assertThatThrownBy(() -> pollService.createPoll(1L, requestBy(2L)))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);

            verify(pollRepository, never()).save(any());
        }

        @Test
        @DisplayName("해당 곡 팀에 속하지 않으면 보컬이어도 조율을 열 수 없다")
        void outsiderCannotOpenPoll() {
            given(songRepository.findById(1L)).willReturn(Optional.of(song));
            given(memberRepository.findById(1L)).willReturn(Optional.of(vocal));
            given(songMemberRepository.existsBySongIdAndMemberId(1L, 1L)).willReturn(false);

            assertThatThrownBy(() -> pollService.createPoll(1L, requestBy(1L)))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("이미 진행 중인 조율이 있으면 새로 열 수 없다")
        void cannotOpenSecondPollWhileOneIsOpen() {
            given(songRepository.findById(1L)).willReturn(Optional.of(song));
            given(memberRepository.findById(1L)).willReturn(Optional.of(vocal));
            given(songMemberRepository.existsBySongIdAndMemberId(1L, 1L)).willReturn(true);
            given(pollRepository.findFirstBySongIdAndStatusOrderByIdDesc(1L, PollStatus.OPEN))
                    .willReturn(Optional.of(pollWithId(100L, vocal)));

            assertThatThrownBy(() -> pollService.createPoll(1L, requestBy(1L)))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Nested
    @DisplayName("confirm")
    class Confirm {

        @Test
        @DisplayName("조율을 만든 보컬이 확정하면 CONFIRMED가 되고 해당 후보만 confirmed로 표시된다")
        void creatorConfirmsSlot() {
            RehearsalPoll poll = pollWithId(100L, vocal);
            RehearsalSlot chosen = slotWithId(200L, poll);
            RehearsalSlot other = slotWithId(201L, poll);
            given(pollRepository.findById(100L)).willReturn(Optional.of(poll));
            given(slotRepository.findById(200L)).willReturn(Optional.of(chosen));
            given(slotRepository.findByPollIdOrderByStartAtAsc(100L)).willReturn(List.of(chosen, other));
            given(songMemberRepository.findBySongId(1L)).willReturn(List.of(new SongMember(song, vocal)));
            given(responseRepository.findBySlot_Poll_Id(100L)).willReturn(List.of());

            PollResponse response = pollService.confirm(100L, new PollConfirmRequest(1L, 200L));

            assertThat(response.status()).isEqualTo(PollStatus.CONFIRMED);
            assertThat(response.slots())
                    .filteredOn(PollResponse.SlotDto::confirmed)
                    .extracting(PollResponse.SlotDto::slotId)
                    .containsExactly(200L);
        }

        @Test
        @DisplayName("조율을 만들지 않은 사람은 확정할 수 없다")
        void nonCreatorCannotConfirm() {
            RehearsalPoll poll = pollWithId(100L, vocal);
            given(pollRepository.findById(100L)).willReturn(Optional.of(poll));

            assertThatThrownBy(() -> pollService.confirm(100L, new PollConfirmRequest(2L, 200L)))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("이미 확정된 조율은 다시 확정할 수 없다")
        void cannotConfirmTwice() {
            RehearsalPoll poll = pollWithId(100L, vocal);
            poll.confirm(slotWithId(200L, poll));
            given(pollRepository.findById(100L)).willReturn(Optional.of(poll));

            assertThatThrownBy(() -> pollService.confirm(100L, new PollConfirmRequest(1L, 200L)))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Nested
    @DisplayName("unconfirm")
    class Unconfirm {

        @Test
        @DisplayName("확정을 취소하면 OPEN으로 돌아가고 후보 시간은 그대로 남는다")
        void creatorCanCancelConfirmation() {
            RehearsalPoll poll = pollWithId(100L, vocal);
            RehearsalSlot slot = slotWithId(200L, poll);
            poll.confirm(slot);
            given(pollRepository.findById(100L)).willReturn(Optional.of(poll));
            given(slotRepository.findByPollIdOrderByStartAtAsc(100L)).willReturn(List.of(slot));
            given(songMemberRepository.findBySongId(1L)).willReturn(List.of(new SongMember(song, vocal)));
            given(responseRepository.findBySlot_Poll_Id(100L)).willReturn(List.of());

            PollResponse response = pollService.unconfirm(100L, new PollUnconfirmRequest(1L));

            assertThat(response.status()).isEqualTo(PollStatus.OPEN);
            // 후보 시간은 유지되고, 확정 표시만 풀린다.
            assertThat(response.slots()).hasSize(1);
            assertThat(response.slots().get(0).confirmed()).isFalse();
        }

        @Test
        @DisplayName("확정한 보컬이 아니면 취소할 수 없다")
        void nonCreatorCannotCancel() {
            RehearsalPoll poll = pollWithId(100L, vocal);
            poll.confirm(slotWithId(200L, poll));
            given(pollRepository.findById(100L)).willReturn(Optional.of(poll));

            assertThatThrownBy(() -> pollService.unconfirm(100L, new PollUnconfirmRequest(2L)))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("아직 확정되지 않은 조율은 취소할 수 없다 (500이 아니라 409)")
        void cannotCancelOpenPoll() {
            RehearsalPoll poll = pollWithId(100L, vocal);
            given(pollRepository.findById(100L)).willReturn(Optional.of(poll));

            assertThatThrownBy(() -> pollService.unconfirm(100L, new PollUnconfirmRequest(1L)))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("확정 취소 후에는 다른 후보 시간으로 다시 확정할 수 있다")
        void canReconfirmAfterCancel() {
            RehearsalPoll poll = pollWithId(100L, vocal);
            RehearsalSlot first = slotWithId(200L, poll);
            RehearsalSlot second = slotWithId(201L, poll);
            poll.confirm(first);
            poll.unconfirm();

            given(pollRepository.findById(100L)).willReturn(Optional.of(poll));
            given(slotRepository.findById(201L)).willReturn(Optional.of(second));
            given(slotRepository.findByPollIdOrderByStartAtAsc(100L)).willReturn(List.of(first, second));
            given(songMemberRepository.findBySongId(1L)).willReturn(List.of(new SongMember(song, vocal)));
            given(responseRepository.findBySlot_Poll_Id(100L)).willReturn(List.of());

            PollResponse response = pollService.confirm(100L, new PollConfirmRequest(1L, 201L));

            assertThat(response.status()).isEqualTo(PollStatus.CONFIRMED);
            assertThat(response.slots())
                    .filteredOn(PollResponse.SlotDto::confirmed)
                    .extracting(PollResponse.SlotDto::slotId)
                    .containsExactly(201L);
        }
    }
}
