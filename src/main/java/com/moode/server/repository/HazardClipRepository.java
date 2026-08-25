package com.moode.server.repository;

import com.moode.server.domain.HazardClip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HazardClipRepository extends JpaRepository<HazardClip, Long> {

    Optional<HazardClip> findByHazardEventId(Long hazardEventId);

    List<HazardClip> findByHazardEventIdIn(List<Long> hazardEventIds);
}
