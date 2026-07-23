package com.woowasoripae.attendance.domain.event;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClubEventRepository extends JpaRepository<ClubEvent, Long> {
    List<ClubEvent> findAllByOrderByEventDateAsc();
}
