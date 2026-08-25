package com.moode.server.repository;

import com.moode.server.domain.HazardActionType;
import com.moode.server.domain.HazardEventAction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HazardEventActionRepository extends JpaRepository<HazardEventAction, Long> {

    boolean existsByHazardEventIdAndActionType(Long hazardEventId, HazardActionType actionType);
}
