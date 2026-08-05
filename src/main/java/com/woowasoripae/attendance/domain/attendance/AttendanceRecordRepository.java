package com.woowasoripae.attendance.domain.attendance;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    List<AttendanceRecord> findByMemberIdAndPracticeDateBetweenOrderByPracticeDateDescScheduledStartTimeDesc(
            Long memberId, LocalDate from, LocalDate to);

    List<AttendanceRecord> findByMemberIdOrderByPracticeDateDescScheduledStartTimeDesc(Long memberId);

    List<AttendanceRecord> findByMethodAndStatusOrderBySubmittedAtAsc(AttendanceMethod method, AttendanceStatus status);

    List<AttendanceRecord> findByPracticeDateAndScheduledStartTime(LocalDate practiceDate, LocalTime scheduledStartTime);

    List<AttendanceRecord> findByPracticeDate(LocalDate practiceDate);

    Optional<AttendanceRecord> findByMemberIdAndPracticeDate(Long memberId, LocalDate practiceDate);

    @Query("select coalesce(sum(a.fineAmount), 0) from AttendanceRecord a where a.member.id = :memberId")
    int sumFineAmountByMemberId(@Param("memberId") Long memberId);

    @Query("select count(a) from AttendanceRecord a where a.member.id = :memberId and a.status = 'ABSENT'")
    long countAbsentByMemberId(@Param("memberId") Long memberId);

    @Query("select coalesce(sum(a.lateMinutes), 0) from AttendanceRecord a where a.member.id = :memberId and a.status = 'LATE'")
    int sumLateMinutesByMemberId(@Param("memberId") Long memberId);

    /** 부원별 누적 벌금 합계. 지각비 미납부자 명단(임원 관리 > 정산)이 사용한다. */
    @Query("select a.member.id as memberId, coalesce(sum(a.fineAmount), 0) as totalFine "
            + "from AttendanceRecord a group by a.member.id having coalesce(sum(a.fineAmount), 0) > 0")
    List<MemberFineTotal> sumFineAmountGroupedByMember();

    interface MemberFineTotal {
        Long getMemberId();

        int getTotalFine();
    }
}
