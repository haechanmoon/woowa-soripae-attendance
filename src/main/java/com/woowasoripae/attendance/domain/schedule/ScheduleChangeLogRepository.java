package com.woowasoripae.attendance.domain.schedule;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleChangeLogRepository extends JpaRepository<ScheduleChangeLog, Long> {

    /** 임원 화면은 이름/파트까지 쓰므로 member를 함께 가져와 N+1을 피한다. 최근 변경이 위로 오게 정렬한다. */
    @Query("select l from ScheduleChangeLog l join fetch l.member "
            + "where l.practiceDate between :start and :end order by l.createdAt desc")
    List<ScheduleChangeLog> findWithMemberByPracticeDateBetween(
            @Param("start") LocalDate start, @Param("end") LocalDate end);
}
