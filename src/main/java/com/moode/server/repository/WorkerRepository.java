package com.moode.server.repository;

import com.moode.server.domain.Worker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkerRepository extends JpaRepository<Worker, Long> {

    Optional<Worker> findByEmployeeNo(String employeeNo);

    // BLOCK_ZONE 조치 시 알림 대상 산정에 쓴다.
    List<Worker> findByCurrentZoneId(Long zoneId);
}
