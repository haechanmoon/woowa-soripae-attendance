package com.woowasoripae.attendance.domain.schedule;

import com.woowasoripae.attendance.domain.member.Member;
import com.woowasoripae.attendance.domain.member.MemberRepository;
import com.woowasoripae.attendance.global.exception.ApiException;
import com.woowasoripae.attendance.web.schedule.dto.ScheduleRegisterRequest;
import com.woowasoripae.attendance.web.schedule.dto.ScheduleResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ScheduleService {

    private final PracticeScheduleRepository practiceScheduleRepository;
    private final MemberRepository memberRepository;

    public ScheduleService(PracticeScheduleRepository practiceScheduleRepository, MemberRepository memberRepository) {
        this.practiceScheduleRepository = practiceScheduleRepository;
        this.memberRepository = memberRepository;
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
