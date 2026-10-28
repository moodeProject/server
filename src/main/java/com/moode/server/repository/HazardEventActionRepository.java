package com.moode.server.repository;

import com.moode.server.domain.HazardActionType;
import com.moode.server.domain.HazardEventAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HazardEventActionRepository extends JpaRepository<HazardEventAction, Long> {

    boolean existsByHazardEventIdAndActionType(Long hazardEventId, HazardActionType actionType);

    /** 상세 화면의 조치 이력. 실행한 순서대로. */
    List<HazardEventAction> findByHazardEventIdOrderByPerformedAtAscIdAsc(Long hazardEventId);
}
