package com.moode.server.repository;

import com.moode.server.domain.HazardEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * 목록 조회 필터(category/severity/status/기간)가 전부 선택값이라
 * JPQL 로 조합하면 분기가 많아져 Specification 을 쓴다.
 */
public interface HazardEventRepository
        extends JpaRepository<HazardEvent, Long>, JpaSpecificationExecutor<HazardEvent> {

    // event_uid 는 UNIQUE 가 아니라 사건당 여러 건일 수 있어 List 로 받는다.
    List<HazardEvent> findByEventUidOrderByOccurredAtAsc(String eventUid);

    /**
     * 필터 탭의 건수 배지용 집계. 현재 필터와 무관하게 항상 전체 기준이라 조건을 걸지 않는다.
     * 카테고리마다 count 를 따로 날리지 않고 한 번에 묶는다.
     */
    @Query("SELECT e.category, COUNT(e) FROM HazardEvent e GROUP BY e.category")
    List<Object[]> countByCategory();
}
