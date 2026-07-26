package com.woowasoripae.attendance.domain.schedule;

import com.woowasoripae.attendance.domain.member.Member;
import com.woowasoripae.attendance.domain.member.MemberRepository;
import com.woowasoripae.attendance.domain.song.SongMemberRepository;
import com.woowasoripae.attendance.global.exception.ApiException;
import com.woowasoripae.attendance.web.schedule.dto.NextWeekRegistrationResponse;
import com.woowasoripae.attendance.web.schedule.dto.ScheduleRegisterRequest;
import com.woowasoripae.attendance.web.schedule.dto.ScheduleResponse;
import com.woowasoripae.attendance.web.schedule.dto.WeeklyScheduleResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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

    /**
     * 한 주(월~일)를 요일별 → 시작 시각별로 묶어 "언제 누가 오는지"를 한 번에 보여준다.
     * 등록이 없는 요일도 빈 상태로 포함해, 화면이 늘 7일을 같은 모양으로 그릴 수 있게 한다.
     */
    public WeeklyScheduleResponse getWeeklySchedule(WeekScope scope) {
        LocalDate weekStart = scope.weekStart(LocalDate.now());
        LocalDate weekEnd = weekStart.plusDays(6);

        Map<LocalDate, List<PracticeSchedule>> byDate = practiceScheduleRepository
                .findWithMemberByPracticeDateBetween(weekStart, weekEnd)
                .stream().collect(Collectors.groupingBy(PracticeSchedule::getPracticeDate));

        List<WeeklyScheduleResponse.DaySchedule> days = new ArrayList<>();
        Set<Long> weeklyMemberIds = new HashSet<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            List<PracticeSchedule> ofDay = byDate.getOrDefault(date, List.of());
            // 한 사람이 하루에 여러 타임을 등록해도 "몇 명 오는지"에서는 한 명이다.
            int memberCount = (int) ofDay.stream().map(s -> s.getMember().getId()).distinct().count();
            ofDay.forEach(s -> weeklyMemberIds.add(s.getMember().getId()));
            days.add(new WeeklyScheduleResponse.DaySchedule(date, date.getDayOfWeek(), memberCount, toSlots(ofDay)));
        }
        // 주 3회 오는 사람이 흔해, 요일별 합계로는 실제 참여 인원을 알 수 없다.
        return new WeeklyScheduleResponse(weekStart, weekEnd, weeklyMemberIds.size(), days);
    }

    /** 같은 시작 시각끼리 한 칸으로 묶는다. 칸은 이른 시간부터, 칸 안의 이름은 이름순으로 고정해 표시가 흔들리지 않게 한다. */
    private List<WeeklyScheduleResponse.TimeSlot> toSlots(List<PracticeSchedule> ofDay) {
        return ofDay.stream()
                .collect(Collectors.groupingBy(PracticeSchedule::getStartTime, TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new WeeklyScheduleResponse.TimeSlot(
                        entry.getKey(),
                        entry.getValue().get(0).getEndTime(),
                        entry.getValue().stream()
                                .map(PracticeSchedule::getMember)
                                .sorted(Comparator.comparing(Member::getName))
                                .map(m -> new WeeklyScheduleResponse.Attendee(m.getId(), m.getName(), m.getPart()))
                                .toList()))
                .toList();
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
