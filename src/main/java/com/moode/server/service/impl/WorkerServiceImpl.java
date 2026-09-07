package com.moode.server.service.impl;

import com.moode.server.domain.SensorData;
import com.moode.server.domain.WorkerState;
import com.moode.server.dto.response.HeatRiskResponse;
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
            List.of(WorkerState.ACTION_REQUIRED, WorkerState.FALLING, WorkerState.FALLEN);

    private final SensorDataRepository sensorDataRepository;
    private final WeatherHeatRiskCache weatherHeatRiskCache;

    @Override
    public List<WorkerStatusResponse> getAllWorkersStatus() {
        return sensorDataRepository.findLatestStatusPerDevice()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public WorkerStatusResponse getWorkerStatus(String deviceId) {
        return sensorDataRepository
                .findTop1ByDeviceIdOrderByRecordedAtDesc(deviceId)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 기기입니다. " + deviceId));
    }

    @Override
    public List<WorkerStatusResponse> getAlerts() {
        return sensorDataRepository.findAlerts(ALERT_STATES)
                .stream()
                .map(this::toResponse)
                .toList();
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
                .findTop1ByDeviceIdOrderByRecordedAtDesc(deviceId)
                .map(this::toHeatRiskResponse)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 기기입니다. " + deviceId));
    }

    private WorkerStatusResponse toResponse(SensorData data) {
        return WorkerStatusResponse.builder()
                .deviceId(data.getDeviceId())
                .fallState(data.getFallState())
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
                .fallConfidence(data.getFallConfidence())
                .posture(data.getPosture())
                .postureAbnormal(isPostureAbnormal(data.getPosture()))
                .build();
    }

    // 몸 신호(heatRiskAbnormal) + 외부 기상(체감온도 캐시)을 조합해 최종 온열질환 위험도를 계산.
    // SensorDataServiceImpl.checkHeatRisk() 와 동일한 규칙. heatRiskLevel 이 DB에 별도 저장되지 않으므로
    // 조회 시점에 다시 계산한다.
    private HeatRiskResponse toHeatRiskResponse(SensorData data) {
        boolean bodySignal = Boolean.TRUE.equals(data.getHeatRiskAbnormal());
        WorkerState externalLevel = weatherHeatRiskCache.currentLevel();
        boolean externalWarning = externalLevel.ordinal() >= WorkerState.RECOMMEND.ordinal();

        WorkerState heatRiskLevel;
        if (bodySignal && externalWarning) {
            heatRiskLevel = WorkerState.ACTION_REQUIRED;
        } else if (bodySignal || externalWarning) {
            heatRiskLevel = WorkerState.RECOMMEND;
        } else {
            heatRiskLevel = WorkerState.NORMAL;
        }

        return HeatRiskResponse.builder()
                .deviceId(data.getDeviceId())
                .recordedAt(data.getRecordedAt())
                .heartRate(data.getHeartRate())
                .spo2(data.getSpo2())
                .hrv(data.getHrv())
                .fatigueAbnormal(data.getFatigueAbnormal())
                .heatRiskAbnormal(data.getHeatRiskAbnormal())
                .externalHeatWarning(externalWarning)
                .heatRiskLevel(heatRiskLevel)
                .build();
    }

    private boolean isPostureAbnormal(String posture) {
        return "STUMBLE".equalsIgnoreCase(posture) || "COLLAPSE".equalsIgnoreCase(posture);
    }
}
