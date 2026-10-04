package com.moode.server.repository;

import com.moode.server.domain.Sos;
import com.moode.server.domain.SosStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SosRepository extends JpaRepository<Sos, Long> {

    // 목록에서 신고자·헬멧·구역을 같이 쓰므로 한 번에 불러온다
    @EntityGraph(attributePaths = {"requester", "helmet", "zone"})
    List<Sos> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"requester", "helmet", "zone"})
    List<Sos> findByStatusOrderByCreatedAtDesc(SosStatus status);
}
