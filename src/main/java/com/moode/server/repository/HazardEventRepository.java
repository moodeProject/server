package com.moode.server.repository;

import com.moode.server.domain.HazardCategory;
import com.moode.server.domain.HazardEvent;
import com.moode.server.domain.HazardStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    // 작업자 관리 상세/목록의 "외부요인" 블록. 아직 처리되지 않은(미해결) 영상 기반 위험 중 가장
    // 최근 것 하나만 있으면 된다 — handle()로 RESOLVED 처리되면 더 이상 뜨지 않는다.
    Optional<HazardEvent> findFirstByWorker_IdAndCategoryAndStatusInOrderByOccurredAtDesc(
            Long workerId, HazardCategory category, List<HazardStatus> statuses);

    // 건강 중복 생성 방지용. 같은 헬멧의 미처리·처리중 건강 사고가 있는지.
    Optional<HazardEvent> findFirstByHelmet_IdAndCategoryAndStatusInOrderByOccurredAtDesc(
            Long helmetId, HazardCategory category, List<HazardStatus> statuses);

    // 추락 중복 병합용. 같은 헬멧의 미처리·처리중 사고 중 시간창 안에 발생한 가장 최근 것 하나.
    Optional<HazardEvent> findFirstByHelmet_IdAndCategoryAndStatusInAndOccurredAtAfterOrderByOccurredAtDesc(
            Long helmetId, HazardCategory category, List<HazardStatus> statuses, LocalDateTime after);
}
