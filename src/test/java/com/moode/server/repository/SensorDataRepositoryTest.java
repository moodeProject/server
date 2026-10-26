package com.moode.server.repository;

import com.moode.server.domain.SensorData;
import com.moode.server.domain.WorkerState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@ActiveProfiles("test")
class SensorDataRepositoryTest {

    @Autowired
    private SensorDataRepository sensorDataRepository;

    private SensorData save(String deviceId, double heartRate) {
        return sensorDataRepository.saveAndFlush(SensorData.builder()
                .deviceId(deviceId)
                .ax(0.0).ay(0.0).az(9.8)
                .gx(0.0).gy(0.0).gz(0.0)
                .heartRate(heartRate)
                .fallState(WorkerState.NORMAL)
                .healthState(WorkerState.NORMAL)
                .build());
    }

    @Test
    @DisplayName("헬멧별로 가장 나중에 들어온 1건씩만 돌려준다")
    void latestPerDevice() {
        save("HELMET-001", 80);
        save("HELMET-002", 90);
        save("HELMET-001", 81);
        save("HELMET-001", 82);
        save("HELMET-002", 91);

        List<SensorData> latest = sensorDataRepository.findLatestStatusPerDevice();

        assertThat(latest)
                .extracting(SensorData::getDeviceId, SensorData::getHeartRate)
                .containsExactlyInAnyOrder(
                        tuple("HELMET-001", 82.0),
                        tuple("HELMET-002", 91.0));
    }

    @Test
    @DisplayName("같은 시각에 여러 건이 들어와도 헬멧 하나가 두 번 나오지 않는다")
    void noDuplicateOnSameRecordedAt() {
        // 0.2초마다 수신해서 recordedAt 정밀도에 따라 동률이 생길 수 있다. 예전 MAX(recordedAt)
        // 비교는 동률이면 같은 헬멧을 여러 행 돌려줬다.
        for (int i = 0; i < 5; i++) {
            save("HELMET-001", 80 + i);
        }

        List<SensorData> latest = sensorDataRepository.findLatestStatusPerDevice();

        assertThat(latest).hasSize(1);
        assertThat(latest.get(0).getHeartRate()).isEqualTo(84.0);
    }
}
