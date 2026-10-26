package com.moode.server.repository;

import com.moode.server.domain.SensorData;
import com.moode.server.domain.WorkerState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SensorDataRepository extends JpaRepository<SensorData, Long> {

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

    /**
     * 헬멧(deviceId)별 가장 최근 수신 1건.
     *
     * <p>예전에는 {@code recordedAt = (같은 deviceId 의 MAX(recordedAt))} 상관 서브쿼리였는데,
     * sensor_data 에 인덱스가 없어 행마다 같은 헬멧 전체를 다시 훑었다. 헬멧이 0.2초마다 보내므로
     * 몇 천 건만 쌓여도 수십 초가 걸려 /api/workers/status, /heat-risk 가 30초 타임아웃·500 을 냈다
     * (로컬 MySQL 재현: 6천 건 24.8초, 2만 건 90초 초과).
     *
     * <p>지금은 deviceId 별 MAX(id) 를 한 번에 구해 그 행만 가져온다 — 테이블을 한 번만 훑는다.
     * id 는 수신 순서대로 증가하므로 "가장 최근" 과 같고, recordedAt 동률로 같은 헬멧이
     * 두 번 나오던 문제도 없어진다.
     */
    @Query("""
        SELECT s FROM SensorData s
        WHERE s.id IN (
            SELECT MAX(s2.id)
            FROM SensorData s2
            GROUP BY s2.deviceId
        )
    """)
    List<SensorData> findLatestStatusPerDevice();
}
