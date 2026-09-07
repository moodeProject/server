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

    /**
     * 직전 수신분. 추락 이벤트를 "상태가 바뀐 순간"에만 만들기 위해 이번 판정과 비교하는 용도다.
     *
     * <p>정렬 기준이 recordedAt 이 아니라 id 인 이유: 펌웨어가 50Hz 로 보내서 같은 초에 여러 건이
     * 쌓이는데 recordedAt 의 소수점 자릿수가 환경마다 달라 동률이 생길 수 있다. 동률이면 어느 행이
     * 직전인지 정해지지 않아 같은 상황에서 이벤트가 다시 만들어진다. id 는 항상 증가하므로 안전하다.
     */
    Optional<SensorData> findTop1ByDeviceIdOrderByIdDesc(String deviceId);

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
