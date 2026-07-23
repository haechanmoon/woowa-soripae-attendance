package com.woowasoripae.attendance.domain.song;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RehearsalSlotRepository extends JpaRepository<RehearsalSlot, Long> {

    List<RehearsalSlot> findByPollIdOrderByStartAtAsc(Long pollId);
}
