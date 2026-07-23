package com.woowasoripae.attendance.domain.song;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RehearsalResponseRepository extends JpaRepository<RehearsalResponse, Long> {

    Optional<RehearsalResponse> findBySlotIdAndMemberId(Long slotId, Long memberId);

    List<RehearsalResponse> findBySlot_Poll_Id(Long pollId);
}
