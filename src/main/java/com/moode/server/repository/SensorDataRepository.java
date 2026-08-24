package com.moode.server.repository;

import com.moode.server.domain.SensorData;
import com.moode.server.domain.WorkerState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SensorDataRepository extends JpaRepository<SensorData, Long> {

    Optional<SensorData> findTop1ByDeviceIdOrderByRecordedAtDesc(String deviceId);

    // 추락/건강 중 하나라도 alertStates에 해당하면 조회
    @Query("""
        SELECT s FROM SensorData s
        WHERE s.fallState IN :alertStates
           OR s.healthState IN :alertStates
    """)
    List<SensorData> findAlerts(@Param("alertStates") List<WorkerState> alertStates);

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
