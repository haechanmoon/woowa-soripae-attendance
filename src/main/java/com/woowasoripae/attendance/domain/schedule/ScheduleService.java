package com.woowasoripae.attendance.domain.schedule;

import com.woowasoripae.attendance.domain.member.Member;
import com.woowasoripae.attendance.domain.member.MemberRepository;
import com.woowasoripae.attendance.domain.song.SongMemberRepository;
import com.woowasoripae.attendance.global.exception.ApiException;
import com.woowasoripae.attendance.web.schedule.dto.ScheduleChangeLogResponse;
import com.woowasoripae.attendance.web.schedule.dto.ScheduleRegisterRequest;
import com.woowasoripae.attendance.web.schedule.dto.ScheduleResponse;
import com.woowasoripae.attendance.web.schedule.dto.ThisWeekChangeRequest;
import com.woowasoripae.attendance.web.schedule.dto.WeekRegistrationResponse;
import com.woowasoripae.attendance.web.schedule.dto.WeeklyScheduleResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
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
    private final ScheduleChangeLogRepository scheduleChangeLogRepository;

    public ScheduleService(PracticeScheduleRepository practiceScheduleRepository, MemberRepository memberRepository,
            SongMemberRepository songMemberRepository, ScheduleChangeLogRepository scheduleChangeLogRepository) {
        this.practiceScheduleRepository = practiceScheduleRepository;
        this.memberRepository = memberRepository;
        this.songMemberRepository = songMemberRepository;
        this.scheduleChangeLogRepository = scheduleChangeLogRepository;
    }

    /**
     * "다음 주 스케줄 등록": 오늘 이후 돌아오는 첫 번째 해당 요일(오늘과 같은 요일이어도 반드시 다음 주)로 등록한다.
     * 출석이 하루 한 번이면 인정되므로 등록도 하루 한 타임만 받는다.
     */
    @Transactional
    public ScheduleResponse register(Long memberId, ScheduleRegisterRequest request) {
        Member member = getMember(memberId);
        LocalDate practiceDate = resolveNextOccurrence(request.dayOfWeek());

        if (!practiceScheduleRepository.findByMemberIdAndPracticeDateOrderByStartTimeAsc(memberId, practiceDate).isEmpty()) {
            throw ApiException.conflict("그날은 이미 등록했어요. 시간을 바꾸려면 기존 스케줄을 지우고 다시 등록해주세요.");
        }

        PracticeSchedule schedule = new PracticeSchedule(member, practiceDate, request.startTime());
        return ScheduleResponse.from(practiceScheduleRepository.save(schedule));
    }

    /**
     * 마감이 지난 뒤 이번 주 스케줄을 바꾼다. 승인을 기다리지 않고 바로 반영하고, 사유와 함께 흔적만 남긴다.
     * 막아두면 "못 가게 됐어요"가 조용한 회피가 되고, 승인을 기다리게 하면 합주를 다녀와도 인증을 못 하기 때문이다.
     * startTime이 비어 있으면 그날 등록을 취소한다는 뜻이라, 등록/시간 변경/취소가 이 한 갈래로 처리된다.
     */
    @Transactional
    public ScheduleResponse changeThisWeek(Long memberId, ThisWeekChangeRequest request) {
        LocalDate today = LocalDate.now();
        LocalDate target = request.practiceDate();
        if (target.isBefore(today)) {
            throw ApiException.badRequest("이미 지난 날은 바꿀 수 없어요.");
        }
        if (target.isAfter(WeekScope.THIS.weekStart(today).plusDays(6))) {
            throw ApiException.badRequest("다음 주는 '다음 주 스케줄 등록'에서 해주세요.");
        }
        Member member = getMember(memberId);

        PracticeSchedule existing = practiceScheduleRepository
                .findByMemberIdAndPracticeDateOrderByStartTimeAsc(memberId, target)
                .stream().findFirst().orElse(null);
        LocalTime previous = existing != null ? existing.getStartTime() : null;

        ScheduleResponse response = request.startTime() == null
                ? cancel(existing, target)
                : upsert(member, existing, target, request.startTime());

        scheduleChangeLogRepository.save(
                new ScheduleChangeLog(member, target, previous, request.startTime(), request.reason()));
        return response;
    }

    private ScheduleResponse cancel(PracticeSchedule existing, LocalDate target) {
        if (existing == null) {
            throw ApiException.notFound("그날은 등록한 스케줄이 없어요. date=" + target);
        }
        practiceScheduleRepository.delete(existing);
        return null;
    }

    /** 하루 한 타임이므로 시각만 옮기면 된다. 지웠다 다시 만들면 유니크 제약과 부딪힌다. */
    private ScheduleResponse upsert(Member member, PracticeSchedule existing, LocalDate target, LocalTime startTime) {
        if (existing == null) {
            return ScheduleResponse.from(practiceScheduleRepository.save(new PracticeSchedule(member, target, startTime)));
        }
        existing.changeStartTime(startTime);
        return ScheduleResponse.from(practiceScheduleRepository.save(existing));
    }

    /** 임원 관리: 마감 후 이번 주에 일어난 변경 내역. 승인 대상이 아니라 "물어볼 거리"를 모아 보여준다. */
    public List<ScheduleChangeLogResponse> getThisWeekChanges() {
        LocalDate weekStart = WeekScope.THIS.weekStart(LocalDate.now());
        return scheduleChangeLogRepository.findWithMemberByPracticeDateBetween(weekStart, weekStart.plusDays(6))
                .stream().map(ScheduleChangeLogResponse::from).toList();
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
     * 임원 관리: 한 주(월~일)에 스케줄을 아직 등록하지 않은 부원을 파악한다.
     * 곡에 배정된 부원(=합주 대상)만 대상으로 한다. 배정이 없는 부원은 등록할 이유가 없어 제외한다.
     * scope를 주지 않으면 오늘 요일에 맞는 주를 고른다. 마감이 일요일이라 주 초에 다음 주가 전원 미등록인 건
     * 이상 신호가 아니어서, 그때는 이미 확정된 이번 주 결과를 먼저 보여주는 편이 쓸모 있다.
     */
    public WeekRegistrationResponse getRegistrationStatus(WeekScope scope) {
        LocalDate today = LocalDate.now();
        WeekScope resolved = scope != null ? scope : WeekScope.defaultForRegistration(today);
        LocalDate weekStart = resolved.weekStart(today);
        LocalDate weekEnd = weekStart.plusDays(6);

        Set<Long> registeredIds = practiceScheduleRepository.findByPracticeDateBetween(weekStart, weekEnd)
                .stream().map(s -> s.getMember().getId()).collect(Collectors.toSet());
        Set<Long> assignedIds = new HashSet<>(songMemberRepository.findDistinctMemberIds());

        List<WeekRegistrationResponse.MemberBrief> registered = new ArrayList<>();
        List<WeekRegistrationResponse.MemberBrief> notRegistered = new ArrayList<>();
        for (Member member : memberRepository.findAll()) {
            if (!assignedIds.contains(member.getId())) {
                continue; // 곡 배정이 없는 부원은 합주 스케줄 등록 대상이 아니므로 집계에서 제외
            }
            var brief = new WeekRegistrationResponse.MemberBrief(member.getId(), member.getName(), member.getPart());
            (registeredIds.contains(member.getId()) ? registered : notRegistered).add(brief);
        }
        // 이번 주는 마감이 지나 더 등록할 수 없으니, 남은 날도 독려도 의미가 없다.
        Integer daysLeft = resolved == WeekScope.NEXT ? WeekScope.daysUntilDeadline(today) : null;
        boolean urgent = daysLeft != null && daysLeft <= WeekScope.URGENT_DAYS;
        return new WeekRegistrationResponse(resolved, weekStart, weekEnd, daysLeft, urgent, registered, notRegistered);
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
