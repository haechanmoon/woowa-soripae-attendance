package com.woowasoripae.attendance.domain.schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PracticeScheduleRepository extends JpaRepository<PracticeSchedule, Long> {

    /** 주간 현황은 부원 이름/파트까지 쓰므로 member를 함께 가져와 N+1을 피한다. */
    @Query("select s from PracticeSchedule s join fetch s.member where s.practiceDate between :start and :end")
    List<PracticeSchedule> findWithMemberByPracticeDateBetween(
            @Param("start") LocalDate start, @Param("end") LocalDate end);

    List<PracticeSchedule> findByMemberIdAndPracticeDateGreaterThanEqualOrderByPracticeDateAscStartTimeAsc(
            Long memberId, LocalDate from);

    List<PracticeSchedule> findByMemberIdAndPracticeDateOrderByStartTimeAsc(Long memberId, LocalDate practiceDate);

    List<PracticeSchedule> findByPracticeDateOrderByStartTimeAsc(LocalDate practiceDate);

    List<PracticeSchedule> findByPracticeDateBetween(LocalDate start, LocalDate end);

    Optional<PracticeSchedule> findByMemberIdAndPracticeDateAndStartTime(Long memberId, LocalDate practiceDate, LocalTime startTime);
}
