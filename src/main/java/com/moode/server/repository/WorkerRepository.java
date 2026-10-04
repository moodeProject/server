package com.moode.server.repository;

import com.moode.server.domain.Worker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkerRepository extends JpaRepository<Worker, Long> {

    Optional<Worker> findByEmployeeNo(String employeeNo);

    // BLOCK_ZONE 조치 시 알림 대상 산정에 쓴다.
    List<Worker> findByCurrentZoneId(Long zoneId);

    /**
     * 작업자 관리 목록 검색. 이름/사번 부분 일치, 비활성(삭제된) 작업자는 제외한다.
     * 상태(정상/주의/위험)는 저장 컬럼이 아니라 조회 후 서비스 계층에서 계산해 걸러낸다 —
     * 데모 규모(작업자 수십 명)라 DB 레벨 집계 대신 애플리케이션에서 필터링/페이징한다.
     */
    @Query("""
        SELECT w FROM Worker w
        WHERE w.active = true
          AND (:q IS NULL OR :q = '' OR LOWER(w.name) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(w.employeeNo) LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY w.id ASC
    """)
    List<Worker> searchActive(@Param("q") String q);
}
