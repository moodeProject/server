package com.moode.server.repository;

import com.moode.server.domain.Helmet;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
