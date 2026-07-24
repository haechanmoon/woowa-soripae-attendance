package com.woowasoripae.attendance.domain.schedule;

import com.woowasoripae.attendance.domain.member.Member;
import com.woowasoripae.attendance.domain.member.MemberRepository;
import com.woowasoripae.attendance.domain.song.SongMemberRepository;
import com.woowasoripae.attendance.global.exception.ApiException;
import com.woowasoripae.attendance.web.schedule.dto.NextWeekRegistrationResponse;
import com.woowasoripae.attendance.web.schedule.dto.ScheduleRegisterRequest;
import com.woowasoripae.attendance.web.schedule.dto.ScheduleResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ScheduleService {

    private final PracticeScheduleRepository practiceScheduleRepository;
    private final MemberRepository memberRepository;
    private final SongMemberRepository songMemberRepository;

    public ScheduleService(PracticeScheduleRepository practiceScheduleRepository, MemberRepository memberRepository,
            SongMemberRepository songMemberRepository) {
        this.practiceScheduleRepository = practiceScheduleRepository;
        this.memberRepository = memberRepository;
        this.songMemberRepository = songMemberRepository;
    }

    /** "다음 주 스케줄 등록": 오늘 이후 돌아오는 첫 번째 해당 요일(오늘과 같은 요일이어도 반드시 다음 주)로 등록한다. */
    @Transactional
    public ScheduleResponse register(Long memberId, ScheduleRegisterRequest request) {
        Member member = getMember(memberId);
        LocalDate practiceDate = resolveNextOccurrence(request.dayOfWeek());

        practiceScheduleRepository.findByMemberIdAndPracticeDateAndStartTime(memberId, practiceDate, request.startTime())
                .ifPresent(existing -> {
                    throw ApiException.conflict("이미 추가된 시간입니다.");
                });

        PracticeSchedule schedule = new PracticeSchedule(member, practiceDate, request.startTime());
        return ScheduleResponse.from(practiceScheduleRepository.save(schedule));
    }

    public List<ScheduleResponse> getUpcomingSchedules(Long memberId) {
        getMember(memberId);
        return practiceScheduleRepository
                .findByMemberIdAndPracticeDateGreaterThanEqualOrderByPracticeDateAscStartTimeAsc(memberId, LocalDate.now())
                .stream().map(ScheduleResponse::from).toList();
    }

    /** 임원 관리 > 대면 출석 체크: 특정 날짜에 누가 등록해 놨는지 확인하기 위한 조회. */
    public List<ScheduleResponse> getSchedulesByDate(LocalDate practiceDate) {
        return practiceScheduleRepository.findByPracticeDateOrderByStartTimeAsc(practiceDate)
                .stream().map(ScheduleResponse::from).toList();
    }

    /**
     * 임원 관리: 다음 주(다음 월~일)에 스케줄을 아직 등록하지 않은 부원을 파악한다.
     * 곡에 배정된 부원(=합주 대상)만 대상으로 한다. 배정이 없는 부원은 등록할 이유가 없어 제외한다.
     */
    public NextWeekRegistrationResponse getNextWeekRegistration() {
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        Set<Long> registeredIds = practiceScheduleRepository.findByPracticeDateBetween(weekStart, weekEnd)
                .stream().map(s -> s.getMember().getId()).collect(Collectors.toSet());
        Set<Long> assignedIds = new HashSet<>(songMemberRepository.findDistinctMemberIds());

        List<NextWeekRegistrationResponse.MemberBrief> registered = new ArrayList<>();
        List<NextWeekRegistrationResponse.MemberBrief> notRegistered = new ArrayList<>();
        for (Member member : memberRepository.findAll()) {
            if (!assignedIds.contains(member.getId())) {
                continue; // 곡 배정이 없는 부원은 합주 스케줄 등록 대상이 아니므로 집계에서 제외
            }
            var brief = new NextWeekRegistrationResponse.MemberBrief(member.getId(), member.getName(), member.getPart());
            (registeredIds.contains(member.getId()) ? registered : notRegistered).add(brief);
        }
        return new NextWeekRegistrationResponse(weekStart, weekEnd, registered, notRegistered);
    }

    @Transactional
    public void delete(Long memberId, Long scheduleId) {
        PracticeSchedule schedule = practiceScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> ApiException.notFound("존재하지 않는 스케줄입니다. id=" + scheduleId));
        if (!schedule.getMember().getId().equals(memberId)) {
            throw ApiException.badRequest("본인의 스케줄만 삭제할 수 있습니다.");
        }
        practiceScheduleRepository.delete(schedule);
    }

    private LocalDate resolveNextOccurrence(DayOfWeek dayOfWeek) {
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return nextMonday.plusDays(dayOfWeek.getValue() - 1L);
    }

    private Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> ApiException.notFound("존재하지 않는 부원입니다. id=" + memberId));
    }
}
