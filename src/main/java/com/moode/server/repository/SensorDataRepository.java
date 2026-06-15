package com.moode.server.repository;

import com.moode.server.domain.SensorData;
import com.moode.server.domain.WorkerState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SensorDataRepository extends JpaRepository<SensorData, Long> {

    Optional<SensorData> findTop1ByDeviceIdOrderByRecordedAtDesc(String deviceId);

    List<SensorData> findByState(WorkerState state);

    @Query("""
        SELECT s FROM SensorData s
        WHERE s.recordedAt = (
            SELECT MAX(s2.recordedAt)
            FROM SensorData s2
            WHERE s2.deviceId = s.deviceId
        )
    """)
    List<SensorData> findLatestStatusPerDevice();
}
