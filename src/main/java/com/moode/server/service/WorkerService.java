package com.moode.server.service;

import com.moode.server.domain.SensorAlertType;
import com.moode.server.dto.response.HeatRiskResponse;
import com.moode.server.dto.response.LatestVitalsResponse;
import com.moode.server.dto.response.WorkerStatusResponse;

import java.util.List;

public interface WorkerService {
    List<WorkerStatusResponse> getAllWorkersStatus();
    WorkerStatusResponse getWorkerStatus(String deviceId);
    List<WorkerStatusResponse> getAlerts(SensorAlertType type);
    LatestVitalsResponse getLatestVitals(String deviceId);
    List<HeatRiskResponse> getAllHeatRiskStatus();
    HeatRiskResponse getHeatRiskStatus(String deviceId);
}
