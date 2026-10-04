package com.moode.server.repository;

import com.moode.server.domain.Helmet;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HelmetRepository extends JpaRepository<Helmet, Long> {

    // worker / zone 을 함께 로딩한다. 수신 직후 SSE push 는 트랜잭션 밖에서 이 값들을 읽으므로
    // 프록시로 두면 LazyInitializationException 이 난다.
    @EntityGraph(attributePaths = {"worker", "worker.currentZone"})
    Optional<Helmet> findByHelmetNo(String helmetNo);

    @EntityGraph(attributePaths = {"worker", "worker.currentZone"})
    Optional<Helmet> findByDeviceId(String deviceId);

    // SEND_WARNING 시 "작업자에게 연결된 기기" 유무 판단에 쓴다.
    boolean existsByWorkerId(Long workerId);

    boolean existsByDeviceId(String deviceId);

    // 작업자가 이미 보유한(활성) 헬멧이 있는지 배정 전 검증용.
    @EntityGraph(attributePaths = {"worker"})
    Optional<Helmet> findByWorker_IdAndActiveTrue(Long workerId);

    /** 헬멧 관리 목록 검색. 헬멧번호 또는 연결된 작업자 이름 부분 일치, 비활성 헬멧은 제외. */
    @Query("""
        SELECT h FROM Helmet h
        LEFT JOIN FETCH h.worker w
        WHERE h.active = true
          AND (:q IS NULL OR :q = '' OR LOWER(h.helmetNo) LIKE LOWER(CONCAT('%', :q, '%'))
               OR (w IS NOT NULL AND LOWER(w.name) LIKE LOWER(CONCAT('%', :q, '%'))))
        ORDER BY h.id ASC
    """)
    List<Helmet> searchActive(@Param("q") String q);
}
