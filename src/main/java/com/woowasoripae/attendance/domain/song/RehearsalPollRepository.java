package com.woowasoripae.attendance.domain.song;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RehearsalPollRepository extends JpaRepository<RehearsalPoll, Long> {

    Optional<RehearsalPoll> findFirstBySongIdAndStatusOrderByIdDesc(Long songId, PollStatus status);

    Optional<RehearsalPoll> findFirstBySongIdOrderByIdDesc(Long songId);
}
