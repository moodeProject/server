package com.moode.server.service.impl;

import com.moode.server.domain.SensorAlertType;
import com.moode.server.domain.SensorData;
import com.moode.server.domain.WorkerState;
import com.moode.server.dto.response.HeatRiskResponse;
import com.moode.server.dto.response.LatestVitalsResponse;
import com.moode.server.dto.response.WorkerStatusResponse;
import com.moode.server.exception.NotFoundException;
import com.moode.server.repository.SensorDataRepository;
import com.moode.server.service.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkerServiceImpl implements WorkerService {

    // 알림 대상으로 취급할 상태
    private static final List<WorkerState> ALERT_STATES =
            List.of(WorkerState.ACTION_REQUIRED, WorkerState.FALLING, WorkerState.FALLEN, WorkerState.UNCONSCIOUS);

    private final SensorDataRepository sensorDataRepository;
    private final WeatherHeatRiskCache weatherHeatRiskCache;
    private final RecentFallCache recentFallCache;

    @Override
    public List<WorkerStatusResponse> getAllWorkersStatus() {
        return sensorDataRepository.findLatestStatusPerDevice()
                .stream()
                .map(this::toLiveResponse)
                .toList();
    }

    @Override
    public WorkerStatusResponse getWorkerStatus(String deviceId) {
        return sensorDataRepository
                .findTop1ByDeviceIdOrderByIdDesc(deviceId)
                .map(this::toLiveResponse)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 기기입니다. " + deviceId));
    }

    @Override
    public List<WorkerStatusResponse> getAlerts(SensorAlertType type) {
        List<SensorData> rows;
        if (type == SensorAlertType.FALL) {
            rows = sensorDataRepository.findByFallStateIn(ALERT_STATES);
        } else if (type == SensorAlertType.HEALTH) {
            rows = sensorDataRepository.findByHealthStateIn(List.of(WorkerState.ACTION_REQUIRED));
        } else {
            rows = sensorDataRepository.findAlerts(ALERT_STATES);
        }
        return rows.stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public LatestVitalsResponse getLatestVitals(String deviceId) {
        SensorData last = sensorDataRepository.findTop1ByDeviceIdOrderByIdDesc(deviceId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 기기입니다. " + deviceId));
        SensorData hr = sensorDataRepository.findTop1ByDeviceIdAndHeartRateGreaterThanOrderByIdDesc(deviceId, 0.0)
                .orElse(null);
        SensorData spo2 = sensorDataRepository.findTop1ByDeviceIdAndSpo2GreaterThanOrderByIdDesc(deviceId, 0.0)
                .orElse(null);
        SensorData hrv = sensorDataRepository.findTop1ByDeviceIdAndHrvGreaterThanOrderByIdDesc(deviceId, 0.0)
                .orElse(null);

        return LatestVitalsResponse.builder()
                .deviceId(deviceId)
                .heartRate(hr == null ? null : hr.getHeartRate())
                .heartRateRecordedAt(hr == null ? null : hr.getRecordedAt())
                .spo2(spo2 == null ? null : spo2.getSpo2())
                .spo2RecordedAt(spo2 == null ? null : spo2.getRecordedAt())
                .hrv(hrv == null ? null : hrv.getHrv())
                .hrvRecordedAt(hrv == null ? null : hrv.getRecordedAt())
                .lastReceivedAt(last.getRecordedAt())
                .build();
    }

    @Override
    public List<HeatRiskResponse> getAllHeatRiskStatus() {
        return sensorDataRepository.findLatestStatusPerDevice()
                .stream()
                .map(this::toHeatRiskResponse)
                .toList();
    }

    @Override
    public HeatRiskResponse getHeatRiskStatus(String deviceId) {
        return sensorDataRepository
                .findTop1ByDeviceIdOrderByIdDesc(deviceId)
                .map(this::toHeatRiskResponse)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 기기입니다. " + deviceId));
    }

    // 실시간 조회용: 최신 줄이 추락이 아니어도 HOLD_SECONDS 안에 추락이 있었으면 그 값을 보여준다.
    private WorkerStatusResponse toLiveResponse(SensorData data) {
        WorkerState fallState = data.getFallState();
        Double fallConfidence = data.getFallConfidence();

        if (!RecentFallCache.isFall(fallState)) {
            var held = recentFallCache.recent(data.getDeviceId());
            if (held.isPresent()) {
                fallState = held.get().state();
                fallConfidence = held.get().confidence();
            }
        }
        return toResponse(data, fallState, fallConfidence);
    }

    // 이력 조회용: DB 원본 그대로
    private WorkerStatusResponse toResponse(SensorData data) {
        return toResponse(data, data.getFallState(), data.getFallConfidence());
    }

    private WorkerStatusResponse toResponse(SensorData data, WorkerState fallState, Double fallConfidence) {
        return WorkerStatusResponse.builder()
                .deviceId(data.getDeviceId())
                .fallState(fallState)
                .healthState(data.getHealthState())
                .recordedAt(data.getRecordedAt())
                .ax(data.getAx())
                .ay(data.getAy())
                .az(data.getAz())
                .gx(data.getGx())
                .gy(data.getGy())
                .gz(data.getGz())
                .heartRate(data.getHeartRate())
                .spo2(data.getSpo2())
                .hrv(data.getHrv())
                .fatigueAbnormal(data.getFatigueAbnormal())
                .heatRiskAbnormal(data.getHeatRiskAbnormal())
                .fallConfidence(fallConfidence)
                .posture(data.getPosture())
                .postureAbnormal(isPostureAbnormal(data.getPosture()))
                .build();
    }

    // heatRiskLevel 이 DB에 별도 저장되지 않으므로 조회 시점에 외부 기상 캐시와 다시 조합한다.
    private HeatRiskResponse toHeatRiskResponse(SensorData data) {
        return HeatRiskResponse.of(data, weatherHeatRiskCache.currentLevel());
    }

    private boolean isPostureAbnormal(String posture) {
        return "STUMBLE".equalsIgnoreCase(posture) || "COLLAPSE".equalsIgnoreCase(posture);
    }
}
