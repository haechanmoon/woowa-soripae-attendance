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

    Optional<AttendanceRecord> findByMemberIdAndPracticeDateAndScheduledStartTime(
            Long memberId, LocalDate practiceDate, LocalTime scheduledStartTime);

    @Query("select coalesce(sum(a.fineAmount), 0) from AttendanceRecord a where a.member.id = :memberId")
    int sumFineAmountByMemberId(@Param("memberId") Long memberId);

    @Query("select count(a) from AttendanceRecord a where a.member.id = :memberId and a.status = 'ABSENT'")
    long countAbsentByMemberId(@Param("memberId") Long memberId);

    @Query("select coalesce(sum(a.lateMinutes), 0) from AttendanceRecord a where a.member.id = :memberId and a.status = 'LATE'")
    int sumLateMinutesByMemberId(@Param("memberId") Long memberId);
}
