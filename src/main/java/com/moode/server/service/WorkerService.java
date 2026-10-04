package com.moode.server.service;

import com.moode.server.dto.response.HeatRiskResponse;
import com.moode.server.dto.response.WorkerStatusResponse;

import java.util.List;

public interface WorkerService {
    List<WorkerStatusResponse> getAlerts();
    List<HeatRiskResponse> getAllHeatRiskStatus();
    HeatRiskResponse getHeatRiskStatus(String deviceId);
}
