package com.moode.server.service.impl;

import com.moode.server.domain.SensorData;
import com.moode.server.domain.WorkerState;
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

    private final SensorDataRepository sensorDataRepository;

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
                .orElseThrow(() -> new NotFoundException("존재하지 않는 기기입니다." + deviceId));
    }

    @Override
    public List<WorkerStatusResponse> getAlerts() {
        return sensorDataRepository.findByState(WorkerState.FALLEN)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private WorkerStatusResponse toResponse(SensorData data) {
        return WorkerStatusResponse.builder()
                .deviceId(data.getDeviceId())
                .state(data.getState())
                .recordedAt(data.getRecordedAt())
                .build();
    }
}
