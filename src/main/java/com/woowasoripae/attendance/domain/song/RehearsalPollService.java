package com.woowasoripae.attendance.domain.song;

import com.woowasoripae.attendance.domain.member.Member;
import com.woowasoripae.attendance.domain.member.MemberRepository;
import com.woowasoripae.attendance.global.exception.ApiException;
import com.woowasoripae.attendance.web.song.dto.PollConfirmRequest;
import com.woowasoripae.attendance.web.song.dto.PollCreateRequest;
import com.woowasoripae.attendance.web.song.dto.PollResponse;
import com.woowasoripae.attendance.web.song.dto.SlotResponseRequest;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RehearsalPollService {

    private static final String VOCAL_PART = "보컬";

    private final SongRepository songRepository;
    private final SongMemberRepository songMemberRepository;
    private final MemberRepository memberRepository;
    private final RehearsalPollRepository pollRepository;
    private final RehearsalSlotRepository slotRepository;
    private final RehearsalResponseRepository responseRepository;

    public RehearsalPollService(
            SongRepository songRepository,
            SongMemberRepository songMemberRepository,
            MemberRepository memberRepository,
            RehearsalPollRepository pollRepository,
            RehearsalSlotRepository slotRepository,
            RehearsalResponseRepository responseRepository
    ) {
        this.songRepository = songRepository;
        this.songMemberRepository = songMemberRepository;
        this.memberRepository = memberRepository;
        this.pollRepository = pollRepository;
        this.slotRepository = slotRepository;
        this.responseRepository = responseRepository;
    }

    /** 보컬이 후보 시간을 올려 새 조율을 연다. 같은 곡에 이미 진행 중인(OPEN) 조율이 있으면 409. */
    @Transactional
    public PollResponse createPoll(Long songId, PollCreateRequest request) {
        Song song = getSong(songId);
        Member creator = getMember(request.creatorMemberId());
        assertTeamMember(songId, creator.getId());
        if (!VOCAL_PART.equals(creator.getPart())) {
            throw ApiException.badRequest("보컬만 합주 시간 후보를 등록할 수 있습니다.");
        }
        pollRepository.findFirstBySongIdAndStatusOrderByIdDesc(songId, PollStatus.OPEN)
                .ifPresent(existing -> {
                    throw ApiException.conflict("이미 진행 중인 합주 시간 조율이 있습니다.");
                });

        RehearsalPoll poll = pollRepository.save(new RehearsalPoll(song, creator));
        List<RehearsalSlot> slots = request.slots().stream()
                .map(slotRequest -> slotRepository.save(new RehearsalSlot(poll, slotRequest.startAt(), slotRequest.endAt())))
                .toList();

        return toPollResponse(poll, slots);
    }

    /** 곡의 가장 최근 조율(OPEN 또는 CONFIRMED)을 조회. */
    public PollResponse getLatestPoll(Long songId) {
        getSong(songId);
        RehearsalPoll poll = pollRepository.findFirstBySongIdOrderByIdDesc(songId)
                .orElseThrow(() -> ApiException.notFound("등록된 합주 시간 조율이 없습니다."));
        return toPollResponse(poll, slotRepository.findByPollIdOrderByStartAtAsc(poll.getId()));
    }

    /** 팀원이 후보 시간 하나에 가능/불가능을 표시(재응답 시 갱신)한다. */
    @Transactional
    public PollResponse respond(Long pollId, Long slotId, SlotResponseRequest request) {
        RehearsalPoll poll = getPoll(pollId);
        if (!poll.isOpen()) {
            throw ApiException.conflict("이미 마감된 조율입니다.");
        }
        RehearsalSlot slot = getSlot(slotId);
        assertSlotBelongsToPoll(slot, pollId);
        Member member = getMember(request.memberId());
        assertTeamMember(poll.getSong().getId(), member.getId());

        responseRepository.findBySlotIdAndMemberId(slotId, member.getId())
                .ifPresentOrElse(
                        existing -> existing.update(request.availability()),
                        () -> responseRepository.save(new RehearsalResponse(slot, member, request.availability())));

        return toPollResponse(poll, slotRepository.findByPollIdOrderByStartAtAsc(pollId));
    }

    /** 조율을 만든 보컬만 최종 시간을 확정할 수 있다. */
    @Transactional
    public PollResponse confirm(Long pollId, PollConfirmRequest request) {
        RehearsalPoll poll = getPoll(pollId);
        if (!poll.isOpen()) {
            throw ApiException.conflict("이미 마감된 조율입니다.");
        }
        if (!poll.getCreatedBy().getId().equals(request.memberId())) {
            throw ApiException.badRequest("이 조율을 생성한 보컬만 확정할 수 있습니다.");
        }
        RehearsalSlot slot = getSlot(request.slotId());
        assertSlotBelongsToPoll(slot, pollId);

        poll.confirm(slot);

        return toPollResponse(poll, slotRepository.findByPollIdOrderByStartAtAsc(pollId));
    }

    private PollResponse toPollResponse(RehearsalPoll poll, List<RehearsalSlot> slots) {
        List<Member> roster = songMemberRepository.findBySongId(poll.getSong().getId()).stream()
                .map(SongMember::getMember)
                .toList();

        Map<Long, Map<Long, Availability>> availabilityBySlot = responseRepository.findBySlot_Poll_Id(poll.getId()).stream()
                .collect(Collectors.groupingBy(
                        response -> response.getSlot().getId(),
                        Collectors.toMap(response -> response.getMember().getId(), RehearsalResponse::getAvailability)));

        Long confirmedSlotId = poll.getConfirmedSlot() != null ? poll.getConfirmedSlot().getId() : null;

        List<PollResponse.SlotDto> slotDtos = slots.stream()
                .map(slot -> toSlotDto(slot, roster, availabilityBySlot.getOrDefault(slot.getId(), Map.of()), confirmedSlotId))
                .toList();

        return new PollResponse(
                poll.getId(), poll.getSong().getId(), poll.getSong().getTitle(), poll.getStatus(),
                poll.getCreatedBy().getId(), poll.getCreatedBy().getName(), slotDtos);
    }

    private PollResponse.SlotDto toSlotDto(
            RehearsalSlot slot, List<Member> roster, Map<Long, Availability> availabilityByMemberId, Long confirmedSlotId
    ) {
        List<PollResponse.MemberResponseDto> memberDtos = roster.stream()
                .map(member -> {
                    Availability availability = availabilityByMemberId.get(member.getId());
                    return new PollResponse.MemberResponseDto(
                            member.getId(), member.getName(), availability != null ? availability.name() : null);
                })
                .toList();
        boolean confirmed = slot.getId().equals(confirmedSlotId);
        return new PollResponse.SlotDto(slot.getId(), slot.getStartAt(), slot.getEndAt(), confirmed, memberDtos);
    }

    private void assertSlotBelongsToPoll(RehearsalSlot slot, Long pollId) {
        if (!slot.getPoll().getId().equals(pollId)) {
            throw ApiException.badRequest("해당 조율의 후보 시간이 아닙니다.");
        }
    }

    private void assertTeamMember(Long songId, Long memberId) {
        if (!songMemberRepository.existsBySongIdAndMemberId(songId, memberId)) {
            throw ApiException.badRequest("해당 곡 팀에 속한 부원이 아닙니다.");
        }
    }

    private Song getSong(Long songId) {
        return songRepository.findById(songId)
                .orElseThrow(() -> ApiException.notFound("존재하지 않는 곡입니다. id=" + songId));
    }

    private Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> ApiException.notFound("존재하지 않는 부원입니다. id=" + memberId));
    }

    private RehearsalPoll getPoll(Long pollId) {
        return pollRepository.findById(pollId)
                .orElseThrow(() -> ApiException.notFound("존재하지 않는 조율입니다. id=" + pollId));
    }

    private RehearsalSlot getSlot(Long slotId) {
        return slotRepository.findById(slotId)
                .orElseThrow(() -> ApiException.notFound("존재하지 않는 후보 시간입니다. id=" + slotId));
    }
}
