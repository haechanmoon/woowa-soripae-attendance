package com.woowasoripae.attendance.domain.schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeScheduleRepository extends JpaRepository<PracticeSchedule, Long> {

    List<PracticeSchedule> findByMemberIdAndPracticeDateGreaterThanEqualOrderByPracticeDateAscStartTimeAsc(
            Long memberId, LocalDate from);

    List<PracticeSchedule> findByMemberIdAndPracticeDateOrderByStartTimeAsc(Long memberId, LocalDate practiceDate);

    Optional<PracticeSchedule> findByMemberIdAndPracticeDateAndStartTime(Long memberId, LocalDate practiceDate, LocalTime startTime);
}
