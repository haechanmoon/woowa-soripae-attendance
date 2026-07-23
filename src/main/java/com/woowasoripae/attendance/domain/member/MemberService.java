package com.woowasoripae.attendance.domain.member;

import com.woowasoripae.attendance.domain.attendance.AttendancePolicyProperties;
import com.woowasoripae.attendance.domain.attendance.AttendanceRecordRepository;
import com.woowasoripae.attendance.global.exception.ApiException;
import com.woowasoripae.attendance.web.attendance.dto.AttendanceRecordResponse;
import com.woowasoripae.attendance.web.member.dto.FineSummaryResponse;
import com.woowasoripae.attendance.web.member.dto.MemberCreateRequest;
import com.woowasoripae.attendance.web.member.dto.MemberDetailResponse;
import com.woowasoripae.attendance.web.member.dto.MemberSummaryResponse;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MemberService {

    private static final int RECENT_HISTORY_SIZE = 5;

    /** 회장/부회장을 임원진 상단에 고정하고, 그 외에는 이름순으로 정렬한다. */
    private static final Comparator<Member> MEMBER_ORDER =
            Comparator.comparingInt(MemberService::officerRank).thenComparing(Member::getName);

    private final MemberRepository memberRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendancePolicyProperties policy;

    public MemberService(
            MemberRepository memberRepository,
            AttendanceRecordRepository attendanceRecordRepository,
            AttendancePolicyProperties policy
    ) {
        this.memberRepository = memberRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.policy = policy;
    }

    /** 부원 목록 조회 API — 홈 헤더 아바타, 임원 관리 > 대면 출석 체크 명단에서 사용. */
    public List<MemberSummaryResponse> getAllMembers() {
        return memberRepository.findAll().stream()
                .sorted(MEMBER_ORDER)
                .map(MemberSummaryResponse::from)
                .toList();
    }

    private static int officerRank(Member member) {
        String position = member.getPosition();
        if (position == null) {
            return 3;
        }
        return switch (position) {
            case "회장" -> 0;
            case "부회장" -> 1;
            default -> 2;
        };
    }

    /** 부원별 누적 벌금 조회 API — 홈 탭 "나의 누적 지각/결석비" 카드. */
    public FineSummaryResponse getFineSummary(Long memberId) {
        Member member = getMember(memberId);
        int totalFine = attendanceRecordRepository.sumFineAmountByMemberId(member.getId());
        long absentCount = attendanceRecordRepository.countAbsentByMemberId(member.getId());
        int lateTotalMinutes = attendanceRecordRepository.sumLateMinutesByMemberId(member.getId());

        int absentFine = (int) absentCount * policy.absentFine();
        int lateFine = lateTotalMinutes * policy.lateFinePerMinute();

        return new FineSummaryResponse(member.getId(), totalFine, absentCount, absentFine, lateTotalMinutes, lateFine);
    }

    /** 임원 관리 > 대면 출석 체크 > 부원 상세 시트. */
    public MemberDetailResponse getMemberDetail(Long memberId) {
        Member member = getMember(memberId);
        int unpaidFine = attendanceRecordRepository.sumFineAmountByMemberId(member.getId());
        List<AttendanceRecordResponse> recentHistory = attendanceRecordRepository
                .findByMemberIdOrderByPracticeDateDescScheduledStartTimeDesc(member.getId())
                .stream()
                .limit(RECENT_HISTORY_SIZE)
                .map(AttendanceRecordResponse::from)
                .toList();

        return new MemberDetailResponse(
                member.getId(), member.getName(),
                member.getPosition(), member.getPart(), member.isOfficer(), unpaidFine, recentHistory);
    }

    @Transactional
    public MemberSummaryResponse createMember(MemberCreateRequest request) {
        Member member = new Member(request.name(), request.position(), request.part());
        return MemberSummaryResponse.from(memberRepository.save(member));
    }

    private Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> ApiException.notFound("존재하지 않는 부원입니다. id=" + memberId));
    }
}
